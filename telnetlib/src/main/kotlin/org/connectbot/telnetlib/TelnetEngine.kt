/*
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.telnetlib

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** An ordered effect. Outbound arrays are complete frames owned by their recipient. */
sealed interface TelnetEffect {
    data class Input(
        val value: TelnetInput,
    ) : TelnetEffect

    data class Outbound(
        val bytes: ByteArray,
    ) : TelnetEffect

    data class StateChanged(
        val snapshot: TelnetSnapshot,
    ) : TelnetEffect
}

/** A bounded processing step, including complete effects preceding any failure. */
data class TelnetResult(
    val consumed: Int,
    val effects: List<TelnetEffect>,
    val failure: TelnetFailure? = null,
)

/**
 * Deterministic, single-owner client engine. No sockets, callbacks, or coroutine state.
 * Feed at most [CHUNK_SIZE] bytes per step; repeat with the unconsumed suffix.
 * A result consumes input or reports a terminal failure; empty input is a no-op.
 * Call [start] once before other operations and [finish] once at peer EOF.
 * Invalid slices, dimensions and oversized writes throw IllegalArgumentException.
 * Starting twice or using an unstarted engine throws IllegalStateException.
 */
class TelnetEngine internal constructor(
    val config: TelnetConfig,
) {
    private enum class Parser { DATA, IAC, NEGOTIATION, SB_OPTION, SB_DATA, SB_IAC }

    private var parser = Parser.DATA
    private var verb = 0
    private var sbOption = 0
    private val sb = ByteArrayOutputStream()
    private val local = Array(256) { QState.NO }
    private val remote = Array(256) { QState.NO }
    private var incomingCr = false
    private var outgoingCr = false
    private var columns = config.columns
    private var rows = config.rows
    private var terminalIndex = 0
    private var charsetAwaitingReply = false
    private var charsetRequested = false
    private var started = false
    private var ended = false
    private var fault: TelnetException? = null
    private var agreedCharset: String? = null
    private var effects = mutableListOf<TelnetEffect>()
    private val data = ByteArrayOutputStream(CHUNK_SIZE)

    val snapshot: TelnetSnapshot
        get() =
            TelnetSnapshot(
                status =
                    if (fault != null) {
                        TelnetStatus.FAILED
                    } else if (ended) {
                        TelnetStatus.PEER_EOF
                    } else {
                        TelnetStatus.OPEN
                    },
                localEcho = remote[1] != QState.YES,
                incomingBinary = remote[0] == QState.YES,
                outgoingBinary = local[0] == QState.YES,
                charset = agreedCharset,
                charsetPending = charsetAwaitingReply,
                failure = fault?.error,
            )

    fun isEnabled(
        option: TelnetOption,
        direction: TelnetDirection,
    ): Boolean = states(direction)[option.code] == QState.YES

    fun start(): TelnetResult {
        check(!started) { "Engine already started" }
        started = true
        return step {
            if (config.profile == TelnetProfile.INTERACTIVE) {
                negotiate(TelnetOption.BINARY, TelnetDirection.LOCAL, true)
                negotiate(TelnetOption.BINARY, TelnetDirection.REMOTE, true)
                negotiate(TelnetOption.TTYPE, TelnetDirection.LOCAL, true)
                negotiate(TelnetOption.NAWS, TelnetDirection.LOCAL, true)
                negotiate(TelnetOption.SGA, TelnetDirection.LOCAL, true)
                negotiate(TelnetOption.SGA, TelnetDirection.REMOTE, true)
            }
        }
    }

    fun feed(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ): TelnetResult {
        checkSlice(bytes.size, offset, length)
        val buffer = ByteBuffer.wrap(bytes, offset, length)
        return feed(buffer)
    }

    /** Advances position by consumed bytes; never changes limit or retains the buffer. */
    fun feed(buffer: ByteBuffer): TelnetResult {
        var consumed = 0
        return step {
            val count = minOf(buffer.remaining(), CHUNK_SIZE)
            repeat(count) {
                val b = buffer.get().toInt() and 255
                consumed++
                parse(b)
            }
            flushData()
        }.copy(consumed = consumed)
    }

    /** Encodes one bounded application slice. A trailing raw CR is paired by the next write or flush. */
    fun write(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ): TelnetResult {
        checkSlice(bytes.size, offset, length)
        require(length <= CHUNK_SIZE) { "Split application writes into bounded chunks" }
        if (!snapshot.outgoingBinary && config.nonBinaryPolicy == NonBinaryPolicy.STRICT_ASCII) {
            if ((offset until offset + length).any {
                    (bytes[it].toInt() and 255) > 127
                }
            ) {
                return invalid(TelnetFailureCode.NON_ASCII_DATA, "Non-ASCII data requires BINARY or compatibility policy")
            }
        }
        return step {
            val wire = ByteArrayOutputStream(length * 2 + 2)
            for (i in offset until offset + length) {
                val b = bytes[i].toInt() and 255
                if (snapshot.outgoingBinary) {
                    quote(wire, b)
                } else {
                    if (outgoingCr) {
                        quote(wire, 13)
                        quote(wire, if (b == 10) 10 else 0)
                        outgoingCr = false
                        if (b == 10) continue
                    }
                    when (b) {
                        13 -> {
                            outgoingCr = true
                        }

                        10 -> {
                            if (config.newlinePolicy == NewlinePolicy.LF_TO_CRLF) quote(wire, 13)
                            quote(wire, 10)
                        }

                        else -> {
                            quote(wire, b)
                        }
                    }
                }
            }
            outbound(wire.toByteArray())
        }
    }

    fun carriageReturn(): TelnetResult =
        step {
            resolveOutgoingCr()
            outbound(if (snapshot.outgoingBinary) byteArrayOf(13) else byteArrayOf(13, 0))
        }

    fun sendCommand(command: TelnetCommand): TelnetResult {
        if (command ==
            TelnetCommand.DATA_MARK
        ) {
            return invalid(TelnetFailureCode.UNSUPPORTED_OPERATION, "TCP urgent-data SYNCH is not supported")
        }
        return step {
            resolveOutgoingCr()
            outbound(byteArrayOf(IAC.toByte(), command.code.toByte()))
        }
    }

    fun requestOption(
        option: TelnetOption,
        direction: TelnetDirection,
        enable: Boolean,
    ): TelnetResult {
        if (enable &&
            !supported(option.code, direction)
        ) {
            return invalid(TelnetFailureCode.UNSUPPORTED_OPERATION, "Unsupported option direction or missing configuration")
        }
        return step { negotiate(option, direction, enable) }
    }

    fun resize(
        columns: Int,
        rows: Int,
    ): TelnetResult {
        checkDimensions(columns, rows)
        return step {
            this.columns = columns
            this.rows = rows
            if (local[31] == QState.YES) {
                resolveOutgoingCr()
                sendSize()
            }
        }
    }

    fun flush(): TelnetResult = step { resolveOutgoingCr() }

    /** Completes valid data and diagnoses a truncated control without dispatching partial SB. */
    fun finish(): TelnetResult =
        step {
            resolveIncomingCr()
            flushData()
            if (parser != Parser.DATA) input(TelnetInput.Diagnostic("Truncated Telnet control at EOF: $parser"))
            ended = true
            effects += TelnetEffect.StateChanged(snapshot)
        }

    fun timeoutCharset(): TelnetResult =
        step {
            if (charsetAwaitingReply) throw TelnetCharsetTimeoutException()
        }

    private fun invalid(
        code: TelnetFailureCode,
        message: String,
    ): TelnetResult = TelnetResult(0, emptyList(), TelnetFailure(code, message))

    private inline fun step(action: () -> Unit): TelnetResult {
        check(started) { "Call start before using the engine" }
        if (fault != null) return TelnetResult(0, emptyList(), fault?.error)
        if (ended) return invalid(TelnetFailureCode.CLOSED, "Engine has ended")
        effects = mutableListOf()
        try {
            action()
        } catch (e: TelnetException) {
            flushData()
            fault = e
            effects += TelnetEffect.StateChanged(snapshot)
        }
        return TelnetResult(0, effects.toList(), fault?.error)
    }

    private fun parse(b: Int) {
        when (parser) {
            Parser.DATA -> {
                if (b == IAC) parser = Parser.IAC else applicationByte(b)
            }

            Parser.IAC -> {
                when (b) {
                    IAC -> {
                        applicationByte(b)
                        parser = Parser.DATA
                    }

                    WILL, WONT, DO, DONT -> {
                        flushData()
                        verb = b
                        parser = Parser.NEGOTIATION
                    }

                    SB -> {
                        flushData()
                        parser = Parser.SB_OPTION
                    }

                    else -> {
                        flushData()
                        when (b) {
                            239 -> if (remote[25] == QState.YES) input(TelnetInput.EndOfRecord)
                            249 -> input(TelnetInput.GoAhead)
                        }
                        parser = Parser.DATA
                    }
                }
            }

            Parser.NEGOTIATION -> {
                receiveNegotiation(b)
                parser = Parser.DATA
            }

            Parser.SB_OPTION -> {
                sbOption = b
                sb.reset()
                parser = Parser.SB_DATA
            }

            Parser.SB_DATA -> {
                if (b == IAC) parser = Parser.SB_IAC else appendSb(b)
            }

            Parser.SB_IAC -> {
                when (b) {
                    IAC -> {
                        appendSb(IAC)
                        parser = Parser.SB_DATA
                    }

                    SE -> {
                        handleSb(sbOption, sb.toByteArray())
                        sb.reset()
                        parser = Parser.DATA
                    }

                    else -> {
                        throw TelnetProtocolException("Invalid IAC sequence in subnegotiation: $b")
                    }
                }
            }
        }
    }

    private fun applicationByte(b: Int) {
        if (snapshot.incomingBinary) {
            data.write(b)
            return
        }
        if (incomingCr) {
            data.write(13)
            incomingCr = false
            if (b == 0) return
        }
        if (config.nonBinaryPolicy == NonBinaryPolicy.STRICT_ASCII && b > 127) {
            throw TelnetProtocolException("Non-ASCII application byte without BINARY")
        }
        if (b == 13) incomingCr = true else data.write(b)
    }

    private fun resolveIncomingCr() {
        if (incomingCr) {
            data.write(13)
            incomingCr = false
        }
    }

    private fun resolveOutgoingCr() {
        if (outgoingCr) {
            outbound(byteArrayOf(13, 0))
            outgoingCr = false
        }
    }

    private fun flushData() {
        if (data.size() > 0) {
            effects += TelnetEffect.Input(TelnetInput.Data(data.toByteArray()))
            data.reset()
        }
    }

    private fun input(value: TelnetInput) {
        resolveIncomingCr()
        flushData()
        effects += TelnetEffect.Input(value)
    }

    private fun outbound(bytes: ByteArray) {
        if (bytes.isNotEmpty()) effects += TelnetEffect.Outbound(bytes)
    }

    private fun appendSb(b: Int) {
        if (sb.size() >=
            config.maxSubnegotiationBytes
        ) {
            throw TelnetProtocolException("Subnegotiation exceeds configured payload limit", TelnetFailureCode.BUFFER_LIMIT)
        }
        sb.write(b)
    }

    private fun states(direction: TelnetDirection): Array<QState> = if (direction == TelnetDirection.LOCAL) local else remote

    private fun supported(
        option: Int,
        direction: TelnetDirection,
    ): Boolean =
        when (option) {
            0, 3 -> true
            1 -> direction == TelnetDirection.REMOTE
            24, 31 -> direction == TelnetDirection.LOCAL
            25 -> direction == TelnetDirection.REMOTE && config.consumeRecords
            42 -> config.charset != null
            else -> false
        }

    private fun negotiate(
        option: TelnetOption,
        direction: TelnetDirection,
        enable: Boolean,
    ) {
        transition(option.code, direction, QMethod.request(states(direction)[option.code], enable))
    }

    private fun receiveNegotiation(option: Int) {
        val direction = if (verb == DO || verb == DONT) TelnetDirection.LOCAL else TelnetDirection.REMOTE
        transition(option, direction, QMethod.receive(states(direction)[option], verb == DO || verb == WILL, supported(option, direction)))
    }

    private fun transition(
        option: Int,
        direction: TelnetDirection,
        next: QTransition,
    ) {
        val state = states(direction)
        val oldEnabled = state[option] == QState.YES
        val newEnabled = next.state == QState.YES
        if (oldEnabled != newEnabled) {
            resolveIncomingCr()
            flushData()
            resolveOutgoingCr()
        }
        state[option] = next.state
        next.send?.let {
            resolveOutgoingCr()
            val v =
                if (direction == TelnetDirection.LOCAL) {
                    if (it) WILL else WONT
                } else {
                    if (it) DO else DONT
                }
            outbound(byteArrayOf(IAC.toByte(), v.toByte(), option.toByte()))
        }
        if (oldEnabled != newEnabled) {
            if (option == 1 && direction == TelnetDirection.REMOTE) input(TelnetInput.EchoChanged(!newEnabled))
            if (option == 31 && direction == TelnetDirection.LOCAL && newEnabled) sendSize()
            if (option == 24 && !newEnabled) terminalIndex = 0
            if (option == 42 && !newEnabled) charsetAwaitingReply = false
        }
        maybeRequestCharset()
        effects += TelnetEffect.StateChanged(snapshot)
    }

    private fun sendSize() {
        subnegotiation(31, byteArrayOf((columns ushr 8).toByte(), columns.toByte(), (rows ushr 8).toByte(), rows.toByte()))
    }

    private fun handleSb(
        option: Int,
        payload: ByteArray,
    ) {
        when (option) {
            24 -> {
                if (local[24] == QState.YES && payload.contentEquals(byteArrayOf(1))) {
                    val name = config.terminalTypes[terminalIndex].toByteArray(Charsets.US_ASCII)
                    subnegotiation(24, byteArrayOf(0) + name)
                    if (terminalIndex < config.terminalTypes.lastIndex) terminalIndex++
                }
            }

            42 -> {
                if (local[42] == QState.YES || remote[42] == QState.YES) handleCharset(payload)
            }
        }
    }

    private fun maybeRequestCharset() {
        if (local[42] == QState.YES && local[0] == QState.YES && remote[0] == QState.YES && !charsetRequested) {
            charsetRequested = true
            charsetAwaitingReply = true
            subnegotiation(42, byteArrayOf(1, ' '.code.toByte()) + config.charset!!.toByteArray(Charsets.US_ASCII))
        }
    }

    private fun handleCharset(payload: ByteArray) {
        if (payload.isEmpty()) {
            subnegotiation(42, byteArrayOf(3))
            return
        }
        when (payload[0].toInt() and 255) {
            1 -> {
                // Incoming server REQUEST takes priority over our simultaneous REQUEST.
                var index = 1
                val tableMarker = "[TTABLE]".toByteArray(Charsets.US_ASCII)
                if (payload.size >= 9 && payload.copyOfRange(1, 9).contentEquals(tableMarker)) {
                    if (payload.size <= 9 || payload[9] == 0.toByte()) {
                        subnegotiation(42, byteArrayOf(3))
                        return
                    }
                    index = 10 // marker plus version; table negotiation is not supported
                }
                if (index >= payload.size || !snapshot.incomingBinary || !snapshot.outgoingBinary) {
                    subnegotiation(42, byteArrayOf(3))
                    return
                }
                val separator = payload[index].toInt() and 255
                val namesBytes = payload.copyOfRange(index + 1, payload.size)
                if (separator == IAC || namesBytes.isEmpty() ||
                    namesBytes.any { (it.toInt() and 255) !in 32..126 && (it.toInt() and 255) != separator }
                ) {
                    subnegotiation(42, byteArrayOf(3))
                    return
                }
                val names = namesBytes.toString(Charsets.US_ASCII).split(separator.toChar())
                val match = names.firstOrNull { it.equals(config.charset, ignoreCase = true) }
                if (match == null || names.any { it.isEmpty() }) {
                    subnegotiation(42, byteArrayOf(3))
                } else {
                    subnegotiation(42, byteArrayOf(2) + match.toByteArray(Charsets.US_ASCII))
                    agreedCharset = config.charset
                    input(TelnetInput.CharsetAgreed(match))
                }
            }

            2 -> {
                if (charsetAwaitingReply) {
                    val name = payload.copyOfRange(1, payload.size).toString(Charsets.US_ASCII)
                    if (!name.equals(
                            config.charset,
                            ignoreCase = true,
                        )
                    ) {
                        throw TelnetProtocolException("Peer accepted a charset we did not offer")
                    }
                    charsetAwaitingReply = false
                    agreedCharset = config.charset
                    input(TelnetInput.CharsetAgreed(name))
                }
            }

            3, 5 -> {
                charsetAwaitingReply = false
            }

            4 -> {
                subnegotiation(42, byteArrayOf(5))
                charsetAwaitingReply = false
            }

            else -> {
                subnegotiation(42, byteArrayOf(3))
            }
        }
        effects += TelnetEffect.StateChanged(snapshot)
    }

    private fun subnegotiation(
        option: Int,
        payload: ByteArray,
    ) {
        resolveOutgoingCr()
        val out = ByteArrayOutputStream(payload.size * 2 + 5)
        out.write(IAC)
        out.write(SB)
        out.write(option)
        payload.forEach { quote(out, it.toInt() and 255) }
        out.write(IAC)
        out.write(SE)
        outbound(out.toByteArray())
    }

    private fun quote(
        out: ByteArrayOutputStream,
        b: Int,
    ) {
        out.write(b)
        if (b == IAC) out.write(b)
    }

    companion object {
        const val CHUNK_SIZE: Int = 8192
        private const val IAC = 255
        private const val SE = 240
        private const val SB = 250
        private const val WILL = 251
        private const val WONT = 252
        private const val DO = 253
        private const val DONT = 254
    }
}

/** Creates a fresh engine after validating configuration. Start it before feeding bytes. */
fun createTelnetEngine(config: TelnetConfig = TelnetConfig()): TelnetEngine = TelnetEngine(config)
