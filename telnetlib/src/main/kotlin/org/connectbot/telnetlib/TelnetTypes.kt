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

/** Negotiation direction: LOCAL is WILL/WONT; REMOTE is DO/DONT. */
enum class TelnetDirection { LOCAL, REMOTE }

/** Options supported by the Telnet client. */
enum class TelnetOption(
    val code: Int,
) {
    BINARY(0),
    ECHO(1),
    SGA(3),
    TTYPE(24),
    EOR(25),
    NAWS(31),
    CHARSET(42),
}

/** In-band commands. TCP urgent-data SYNCH is deliberately not represented. */
enum class TelnetCommand(
    val code: Int,
) {
    NOP(241),
    DATA_MARK(242),
    BREAK(243),
    INTERRUPT_PROCESS(244),
    ABORT_OUTPUT(245),
    ARE_YOU_THERE(246),
    ERASE_CHARACTER(247),
    ERASE_LINE(248),
    GO_AHEAD(249),
}

enum class TelnetProfile { PASSIVE, INTERACTIVE }

enum class NonBinaryPolicy { STRICT_ASCII, EIGHT_BIT_COMPATIBILITY }

enum class NewlinePolicy { PRESERVE_LF, LF_TO_CRLF }

enum class TelnetStatus { OPEN, PEER_EOF, LOCAL_CLOSED, FAILED, CANCELLED }

/**
 * Immutable protocol settings. Terminal capabilities must match the actual emulator.
 * @throws IllegalArgumentException for invalid capabilities, dimensions, budgets or deadlines.
 */
class TelnetConfig(
    terminalTypes: List<String> = listOf("UNKNOWN"),
    val columns: Int = 0,
    val rows: Int = 0,
    val profile: TelnetProfile = TelnetProfile.PASSIVE,
    val nonBinaryPolicy: NonBinaryPolicy = NonBinaryPolicy.STRICT_ASCII,
    val newlinePolicy: NewlinePolicy = NewlinePolicy.PRESERVE_LF,
    val consumeRecords: Boolean = false,
    val charset: String? = null,
    val maxSubnegotiationBytes: Int = 64 * 1024,
    val inputBudgetBytes: Int = 256 * 1024,
    val outputBudgetBytes: Int = 256 * 1024,
    val maxPendingEvents: Int = 8192,
    val controlBudgetBytes: Int = 32 * 1024,
    val charsetTimeoutMillis: Long = 10_000,
    val closeTimeoutMillis: Long = 1_000,
) {
    val terminalTypes: List<String> = java.util.Collections.unmodifiableList(terminalTypes.toList())

    init {
        require(this.terminalTypes.isNotEmpty())
        this.terminalTypes.forEach { require(it.isNotEmpty() && it.length <= 255 && it.all { c -> c.code in 32..126 }) }
        checkDimensions(columns, rows)
        require(charset == null || (charset.isNotEmpty() && charset.length <= 255 && charset.all { it.code in 33..126 }))
        require(maxSubnegotiationBytes in 1..1024 * 1024)
        require(inputBudgetBytes >= TelnetEngine.CHUNK_SIZE + 1)
        require(outputBudgetBytes >= 2 * TelnetEngine.CHUNK_SIZE + 2)
        require(maxPendingEvents >= TelnetEngine.CHUNK_SIZE)
        require(controlBudgetBytes >= 1024 && controlBudgetBytes <= outputBudgetBytes)
        require(charsetTimeoutMillis in 1..86_400_000)
        require(closeTimeoutMillis in 1..86_400_000)
    }
}

/** Lossless, ordered application input; each Data owns its array. */
sealed interface TelnetInput {
    data class Data(
        val bytes: ByteArray,
    ) : TelnetInput

    data object EndOfRecord : TelnetInput

    data object GoAhead : TelnetInput

    data class EchoChanged(
        val localEcho: Boolean,
    ) : TelnetInput

    data class CharsetAgreed(
        val name: String,
    ) : TelnetInput

    data class Diagnostic(
        val message: String,
    ) : TelnetInput
}

data class TelnetSnapshot(
    val status: TelnetStatus = TelnetStatus.OPEN,
    val localEcho: Boolean = true,
    val incomingBinary: Boolean = false,
    val outgoingBinary: Boolean = false,
    val charset: String? = null,
    val charsetPending: Boolean = false,
    val failure: TelnetFailure? = null,
)

/** Stable failure categories returned at public API boundaries. */
enum class TelnetFailureCode {
    CLOSED,
    UNSUPPORTED_OPERATION,
    NON_ASCII_DATA,
    PROTOCOL_ERROR,
    BUFFER_LIMIT,
    TRANSPORT_ERROR,
    CONNECT_TIMEOUT,
    CHARSET_TIMEOUT,
    INTERNAL_ERROR,
}

/** An operational failure returned as data; argument and state preconditions still throw. */
data class TelnetFailure(
    val code: TelnetFailureCode,
    val message: String,
)

sealed interface TelnetOutcome<out T> {
    data class Success<T>(
        val value: T,
    ) : TelnetOutcome<T>

    data class Failure(
        val error: TelnetFailure,
    ) : TelnetOutcome<Nothing>
}

internal open class TelnetException(
    val error: TelnetFailure,
    cause: Throwable? = null,
) : Exception(error.message, cause)

internal class TelnetProtocolException(
    message: String,
    code: TelnetFailureCode = TelnetFailureCode.PROTOCOL_ERROR,
) : TelnetException(TelnetFailure(code, message))

internal class TelnetTransportException(
    message: String,
    cause: Throwable? = null,
) : TelnetException(TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, message), cause)

internal class TelnetClosedException(
    message: String,
) : TelnetException(TelnetFailure(TelnetFailureCode.CLOSED, message))

internal class TelnetCharsetTimeoutException :
    TelnetException(TelnetFailure(TelnetFailureCode.CHARSET_TIMEOUT, "CHARSET transaction timed out"))

internal fun checkSlice(
    size: Int,
    offset: Int,
    length: Int,
) {
    require(offset >= 0 && length >= 0 && offset <= size && length <= size - offset) { "Invalid byte slice" }
}

internal fun checkDimensions(
    columns: Int,
    rows: Int,
) {
    require(columns in 0..65535 && rows in 0..65535) { "Dimensions must be unsigned 16-bit character counts" }
}
