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

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class TelnetEngineTest {
    private fun engine(config: TelnetConfig = TelnetConfig()): TelnetEngine = createTelnetEngine(config).also { it.start() }

    @Test
    fun `trace is independent of every split and random chunking`() {
        val trace =
            "login\r\n".toByteArray() + bytes(255, 251, 1, 255, 253, 31, 255, 250, 24, 1, 255, 240, 255, 251, 25) +
                "prompt\r\u0000".toByteArray() + bytes(255, 239, 255, 249, 255, 251, 0, 255, 255, 200, 255, 252, 0) + "done".toByteArray()
        val config = TelnetConfig(consumeRecords = true, columns = 255, rows = 511)
        val expected = trace(config, listOf(trace))
        for (split in 0..trace.size) {
            assertEquals(
                expected,
                trace(config, listOf(trace.copyOfRange(0, split), trace.copyOfRange(split, trace.size))),
            )
        }
        val random = Random(854)
        repeat(100) {
            val chunks = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < trace.size) {
                val end = minOf(trace.size, offset + random.nextInt(1, 12))
                chunks += trace.copyOfRange(offset, end)
                offset = end
            }
            assertEquals(expected, trace(config, chunks))
        }
    }

    private fun trace(
        config: TelnetConfig,
        chunks: List<ByteArray>,
    ): List<String> {
        val engine = engine(config)
        val result = mutableListOf<String>()
        val effects = chunks.flatMap { engine.feed(it).effects } + engine.finish().effects
        for (effect in effects) {
            val text =
                when (effect) {
                    is TelnetEffect.Input -> {
                        when (val input = effect.value) {
                            is TelnetInput.Data -> "data:" + input.bytes.joinToString(",") { (it.toInt() and 255).toString() }
                            else -> "event:$input"
                        }
                    }

                    is TelnetEffect.Outbound -> {
                        "wire:" + effect.bytes.joinToString(",") { (it.toInt() and 255).toString() }
                    }

                    is TelnetEffect.StateChanged -> {
                        "state:${effect.snapshot}"
                    }
                }
            if (text.startsWith("data:") && result.lastOrNull()?.startsWith("data:") == true) {
                result[result.lastIndex] += "," + text.removePrefix("data:")
            } else {
                result += text
            }
        }
        return result
    }

    @Test
    fun `binary directions are independent and IAC is always quoted`() {
        val engine = engine(TelnetConfig(nonBinaryPolicy = NonBinaryPolicy.EIGHT_BIT_COMPATIBILITY))
        engine.feed(bytes(255, 251, 0))
        assertEquals(true, engine.snapshot.incomingBinary)
        assertEquals(false, engine.snapshot.outgoingBinary)
        assertEquals(listOf(13, 0, 255, 255), (engine.write(bytes(13, 255)).wire() + engine.flush().wire()))
        assertContentEquals(bytes(13, 0, 255), assertIs<TelnetInput.Data>(engine.feed(bytes(13, 0, 255, 255)).inputs().single()).bytes)
        engine.feed(bytes(255, 253, 0))
        assertEquals(listOf(13, 0, 255, 255), engine.write(bytes(13, 0, 255)).wire())
    }

    @Test
    fun `NVT pairing survives writes flushes and binary boundaries`() {
        val engine = engine()
        assertEquals(emptyList(), engine.write(bytes(13)).wire())
        assertEquals(listOf(13, 10), engine.write(bytes(10)).wire())
        engine.write(bytes(13))
        assertEquals(listOf(13, 0), engine.flush().wire())
        assertEquals(listOf(13, 0), engine.carriageReturn().wire())
        assertEquals(emptyList(), engine.feed(bytes(13)).inputs())
        val transition = engine.feed(bytes(255, 251, 0, 0))
        assertContentEquals(bytes(13), assertIs<TelnetInput.Data>(transition.inputs().first()).bytes)
        assertContentEquals(bytes(0), assertIs<TelnetInput.Data>(transition.inputs().last()).bytes)
        val convert = engine(TelnetConfig(newlinePolicy = NewlinePolicy.LF_TO_CRLF))
        assertEquals(listOf(13, 10), convert.write(bytes(10)).wire())
        assertEquals(TelnetFailureCode.NON_ASCII_DATA, engine.write(bytes(200)).failure?.code)
    }

    @Test
    fun `NAWS caches dimensions escapes all 255 octets and disables updates`() {
        val engine = engine()
        assertEquals(emptyList(), engine.resize(65535, 255).wire())
        assertEquals(listOf(255, 251, 31, 255, 250, 31, 255, 255, 255, 255, 0, 255, 255, 255, 240), engine.feed(bytes(255, 253, 31)).wire())
        assertEquals(emptyList(), engine.feed(bytes(255, 253, 31)).wire())
        engine.feed(bytes(255, 254, 31))
        assertEquals(emptyList(), engine.resize(80, 24).wire())
        assertEquals(listOf(255, 251, 31, 255, 250, 31, 0, 80, 0, 24, 255, 240), engine.feed(bytes(255, 253, 31)).wire())
        assertFailsWith<IllegalArgumentException> { engine.resize(-1, 24) }
        assertFailsWith<IllegalArgumentException> { engine.resize(80, 65536) }
    }

    @Test
    fun `TTYPE sends only enabled configured types and repeats final type`() {
        val engine = engine(TelnetConfig(terminalTypes = listOf("xterm", "vt100")))
        val send = bytes(255, 250, 24, 1, 255, 240)
        assertEquals(emptyList(), engine.feed(send).wire())
        engine.feed(bytes(255, 253, 24))
        for (type in listOf("xterm", "vt100", "vt100")) {
            assertEquals(
                (bytes(255, 250, 24, 0) + type.toByteArray() + bytes(255, 240)).map { it.toInt() and 255 },
                engine.feed(send).wire(),
            )
        }
    }

    @Test
    fun `unknown SB is bounded and escaped IAC never terminates it`() {
        val engine = engine(TelnetConfig(maxSubnegotiationBytes = 2))
        assertNull(engine.feed(bytes(255, 250, 200, 255, 255, 1, 255, 240)).failure)
        assertEquals(TelnetFailureCode.BUFFER_LIMIT, engine.feed(bytes(255, 250, 200, 1, 2, 3)).failure?.code)
        for (invalid in listOf(250, 251, 1)) {
            val result = engine().feed("valid".toByteArray() + bytes(255, 250, 24, 1, 255, invalid))
            assertContentEquals("valid".toByteArray(), assertIs<TelnetInput.Data>(result.inputs().single()).bytes)
            assertEquals(TelnetFailureCode.PROTOCOL_ERROR, result.failure?.code)
        }
    }

    @Test
    fun `EOF diagnoses every partial parser state and preserves pending CR`() {
        val prefixes = listOf(bytes(), bytes(255), bytes(255, 251), bytes(255, 250), bytes(255, 250, 24), bytes(255, 250, 24, 255))
        for (prefix in prefixes) {
            val engine = engine()
            engine.feed(prefix)
            val result = engine.finish()
            assertNull(result.failure)
            assertEquals(if (prefix.isEmpty()) 0 else 1, result.inputs().filterIsInstance<TelnetInput.Diagnostic>().size)
        }
        val engine = engine()
        engine.feed(bytes(13))
        assertContentEquals(bytes(13), assertIs<TelnetInput.Data>(engine.finish().inputs().single()).bytes)
    }

    @Test
    fun `buffer slices positions limits and ownership are exact`() {
        for (direct in listOf(false, true)) {
            val writable = if (direct) ByteBuffer.allocateDirect(6) else ByteBuffer.allocate(6)
            writable.put(bytes(0, 65, 66, 67, 0, 0)).position(1).limit(4)
            val readOnly = writable.asReadOnlyBuffer()
            val result = engine().feed(readOnly)
            assertEquals(3, result.consumed)
            assertEquals(4, readOnly.position())
            assertEquals(4, readOnly.limit())
            assertContentEquals(bytes(65, 66, 67), assertIs<TelnetInput.Data>(result.inputs().single()).bytes)
        }
        val source = bytes(0, 65, 66, 0)
        val result = engine().feed(source, 1, 2)
        source.fill(0)
        assertContentEquals(bytes(65, 66), assertIs<TelnetInput.Data>(result.inputs().single()).bytes)
        assertEquals(TelnetEngine.CHUNK_SIZE, engine().feed(ByteArray(TelnetEngine.CHUNK_SIZE * 2) { 65 }).consumed)
        assertFailsWith<IllegalArgumentException> { engine().feed(source, Int.MAX_VALUE, 2) }
        assertEquals(0, engine().feed(source, source.size, 0).consumed)
    }

    @Test
    fun `CHARSET accepts only configured names rejects tables and handles simultaneous request`() {
        val engine = engine(TelnetConfig(charset = "UTF-8"))
        engine.feed(bytes(255, 253, 0, 255, 251, 0, 255, 251, 42))

        fun request(payload: ByteArray): TelnetResult = engine.feed(bytes(255, 250, 42) + payload + bytes(255, 240))
        val accepted = request(bytes(1, 59) + "ASCII;utf-8".toByteArray())
        assertEquals((bytes(255, 250, 42, 2) + "utf-8".toByteArray() + bytes(255, 240)).map { it.toInt() and 255 }, accepted.wire())
        assertEquals(TelnetInput.CharsetAgreed("utf-8"), accepted.inputs().single())
        assertEquals(listOf(255, 250, 42, 3, 255, 240), request(bytes()).wire())
        assertEquals(listOf(255, 250, 42, 3, 255, 240), request(bytes(1, 59)).wire())
        assertEquals(listOf(255, 250, 42, 5, 255, 240), request(bytes(4, 1)).wire())
        engine.feed(bytes(255, 253, 42))
        assertEquals(true, engine.snapshot.charsetPending)
        assertEquals(2, request(bytes(1, 59) + "UTF-8".toByteArray()).wire()[3])
        request(bytes(3))
        assertEquals(false, engine.snapshot.charsetPending)
        val fresh = engine(TelnetConfig(charset = "UTF-8"))
        fresh.feed(bytes(255, 253, 0, 255, 251, 0, 255, 253, 42))
        assertEquals(TelnetFailureCode.CHARSET_TIMEOUT, fresh.timeoutCharset().failure?.code)
    }
}
