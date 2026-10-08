/

# ConnectBot Telnet Library

A Kotlin/JVM Telnet **client** library with a byte-driven engine, an ordered coroutine session, and an optional Ktor TCP backend.

## Dependencies

```kotlin
dependencies {
    implementation("org.connectbot.telnetlib:telnetlib:VERSION")
    // Add only when the library should own a TCP connection:
    implementation("org.connectbot.telnetlib:telnetlib-ktor:VERSION")
}
```

Both coordinates share a version. The core depends on Kotlin coroutines and has no Ktor dependency. These coordinates describe the configured Maven publications.

## Explicit outcomes

Operational failures are returned as `TelnetOutcome` values. Invalid configuration, byte slices, dimensions, and endpoint arguments throw `IllegalArgumentException`; invalid engine lifecycle and concurrent receivers throw `IllegalStateException`.  These are programming preconditions, following Kotlin's `require`/`check` conventions.  `TelnetConfig` validates when constructed. `createTelnetEngine` and `openTelnetSession` return their objects directly; `connectTelnet` returns an outcome because connection can fail during normal use.

```kotlin
when (val result = session.resize(columns, rows)) {
    is TelnetOutcome.Success -> Unit
    is TelnetOutcome.Failure -> handleFailure(result.error.code, result.error.message)
}
```

Engine operations return `TelnetResult`, including consumption, ordered effects, and an optional `TelnetFailure`. Rejected arguments leave an otherwise healthy engine or session unchanged. Protocol and transport failures terminate a session.  `TelnetFailureCode` provides named categories; messages are diagnostic text, not a machine-readable interface. Internal operational exceptions are caught at the boundary. Coroutine cancellation retains its standard Kotlin behavior and propagates `CancellationException`.

## Ktor session

```kotlin
val result = connectTelnet(
    scope = sessionScope,
    host = host,
    config = TelnetConfig(
        terminalTypes = listOf("xterm-256color"), // only if the emulator supports it
        columns = 80,
        rows = 24,
        profile = TelnetProfile.INTERACTIVE,
    ),
    ioContext = ioDispatcher,
)
when (result) {
    is TelnetOutcome.Failure -> handleFailure(result.error.code, result.error.message)
    is TelnetOutcome.Success -> {
        val session = result.value
        while (true) {
            when (val received = session.receive()) {
                is TelnetOutcome.Failure -> {
                    handleFailure(received.error.code, received.error.message)
                    break
                }
                is TelnetOutcome.Success -> when (val input = received.value) {
                    null -> break
                    is TelnetInput.Data -> consumeApplicationBytes(input.bytes)
                    is TelnetInput.EchoChanged -> updateInputEchoPolicy(input.localEcho)
                    is TelnetInput.CharsetAgreed -> recordCharsetAgreement(input.name)
                    TelnetInput.EndOfRecord -> recordBoundary()
                    TelnetInput.GoAhead -> goAheadBoundary()
                    is TelnetInput.Diagnostic -> recordDiagnostic(input.message)
                }
            }
        }
        session.close()
    }
}
```

Port defaults to 23; connection timeout defaults to ten seconds. An operation's own connection timeout returns `CONNECT_TIMEOUT`; cancellation by its caller or parent scope propagates normally. A session owns and closes its transport, socket, and selector. One coroutine consumes `receive`; concurrent receivers throw `IllegalStateException`. A successful receive with a null value means EOF after preceding data/events have been drained.

`write` accepts application bytes and handles IAC quoting and NVT conversion. It copies accepted chunks and acknowledges backend completion. Large writes are split into 8 KiB chunks; competing operations may be accepted between them. Encode application text in the consumer. `flush` is a backend barrier, not a peer acknowledgment. Use `carriageReturn` for keyboard Enter; a trailing raw CR from `write` is retained for pairing until another write or a barrier resolves it.

## Supply your own bytes or transport

```kotlin
val engine = createTelnetEngine(TelnetConfig())
consumeEffects(engine.start())
while (incomingBuffer.hasRemaining()) {
    val step = engine.feed(incomingBuffer) // java.nio.ByteBuffer
    consumeEffects(step) // dispatch effects in order, including outbound frames
    if (step.failure != null) break
}
consumeEffects(engine.finish()) // at peer EOF, not at each read boundary
```

The same engine accepts `ByteArray` slices. Each feed consumes at most 8 KiB and reports its exact consumption. Buffer position advances, limit stays unchanged, and direct/read-only buffers are supported. Returned arrays belong to their recipient; caller buffers are never retained. Use the engine on a single owner and process every outbound effect; it performs no I/O.

For coroutine coordination over another backend, implement `TelnetTransport` and call `openTelnetSession(scope, transport, config)`. Reads may be partial, return -1 at EOF, and must honor their destination slice. Writes finish their entire source slice. `close` must unblock pending I/O. Backend exceptions are translated into returned transport failures by the session.

## Supported protocol

The library provides RFC 1143 negotiation in both directions, directional BINARY, remote ECHO, SGA, TTYPE, remote EOR, NAWS, and opt-in fixed-charset CHARSET. Passive startup is the default. Interactive startup requests BINARY both ways and offers TTYPE, NAWS, and SGA.

Strict NVT ASCII is the default. Select `EIGHT_BIT_COMPATIBILITY` explicitly for legacy nonbinary eight-bit servers. Bare LF is preserved unless `LF_TO_CRLF` is selected. Binary mode still interprets commands and quotes IAC.

CHARSET only agrees to `config.charset`; it never changes a decoder. Request the local CHARSET option explicitly to offer that charset; incoming requests can also initiate agreement. Agreement requires BINARY both ways. Translation tables are refused. Pending outbound text has byte limits and a configurable ten-second deadline. Echo events describe negotiation; the consumer implements local display echo and user overrides.

Input/output byte budgets default to 256 KiB each, SB payloads to 64 KiB, and control output to a separate 32 KiB reserve. Queue entry counts are also bounded. Explicit close attempts an ordered flush and interrupts a stalled writer after `closeTimeoutMillis` (one second by default).  Budget pressure suspends producers without dropping data; negotiation saturation or oversized SB fails explicitly. Read buffers and one bounded engine effect batch add fixed, bounded overhead beyond application queue budgets.

Optional extensions, dynamic charset switching, server mode, compression, TLS, TCP urgent-data SYNCH, and a byte-only adapter are unsupported. Ordinary in-band DATA MARK is not offered as a substitute for SYNCH.

## Development and releases

```sh
./gradlew verify
./gradlew :telnetlib:test :telnetlib-ktor:test
./gradlew :telnetlib:metalavaGenerateSignature :telnetlib-ktor:metalavaGenerateSignature
PYTHONPATH=.github/scripts python3 -m unittest discover -s .github/scripts/tests -v
actionlint
```

`verify` includes formatting, tests, API signature/compatibility checks, coverage, documentation, and unsigned local Maven publication assembly under `build/repository`.

## All modules:

| Name |
|---|
| [ConnectBot Telnet Ktor Backend](telnetlib-ktor/index.md) |  |
| [ConnectBot Telnet Library](telnetlib/index.md) |  |

<!-- BEGIN DOCS API CHANGES -->
## New and changed APIs

First release; public APIs listed below.

### Telnet

#### org.connectbot.telnetlib.NewlinePolicy

- Added: [LF_TO_CRLF](telnetlib/org.connectbot.telnetlib/-newline-policy/-l-f_-t-o_-c-r-l-f/index.html)

- Added: [PRESERVE_LF](telnetlib/org.connectbot.telnetlib/-newline-policy/-p-r-e-s-e-r-v-e_-l-f/index.html)

- Added: [enum NewlinePolicy : Enum<NewlinePolicy> ](telnetlib/org.connectbot.telnetlib/-newline-policy/index.html)

#### org.connectbot.telnetlib.NonBinaryPolicy

- Added: [EIGHT_BIT_COMPATIBILITY](telnetlib/org.connectbot.telnetlib/-non-binary-policy/-e-i-g-h-t_-b-i-t_-c-o-m-p-a-t-i-b-i-l-i-t-y/index.html)

- Added: [STRICT_ASCII](telnetlib/org.connectbot.telnetlib/-non-binary-policy/-s-t-r-i-c-t_-a-s-c-i-i/index.html)

- Added: [enum NonBinaryPolicy : Enum<NonBinaryPolicy> ](telnetlib/org.connectbot.telnetlib/-non-binary-policy/index.html)

#### org.connectbot.telnetlib.TelnetCommand

- Added: [ABORT_OUTPUT](telnetlib/org.connectbot.telnetlib/-telnet-command/-a-b-o-r-t_-o-u-t-p-u-t/index.html)

- Added: [ARE_YOU_THERE](telnetlib/org.connectbot.telnetlib/-telnet-command/-a-r-e_-y-o-u_-t-h-e-r-e/index.html)

- Added: [BREAK](telnetlib/org.connectbot.telnetlib/-telnet-command/-b-r-e-a-k/index.html)

- Added: [DATA_MARK](telnetlib/org.connectbot.telnetlib/-telnet-command/-d-a-t-a_-m-a-r-k/index.html)

- Added: [ERASE_CHARACTER](telnetlib/org.connectbot.telnetlib/-telnet-command/-e-r-a-s-e_-c-h-a-r-a-c-t-e-r/index.html)

- Added: [ERASE_LINE](telnetlib/org.connectbot.telnetlib/-telnet-command/-e-r-a-s-e_-l-i-n-e/index.html)

- Added: [GO_AHEAD](telnetlib/org.connectbot.telnetlib/-telnet-command/-g-o_-a-h-e-a-d/index.html)

- Added: [INTERRUPT_PROCESS](telnetlib/org.connectbot.telnetlib/-telnet-command/-i-n-t-e-r-r-u-p-t_-p-r-o-c-e-s-s/index.html)

- Added: [NOP](telnetlib/org.connectbot.telnetlib/-telnet-command/-n-o-p/index.html)

- Added: [val code: Int](telnetlib/org.connectbot.telnetlib/-telnet-command/code.html)

- Added: [enum TelnetCommand : Enum<TelnetCommand> ](telnetlib/org.connectbot.telnetlib/-telnet-command/index.html)

#### org.connectbot.telnetlib.TelnetConfig

- Added: [constructor(terminalTypes: List<String> = listOf("UNKNOWN"), columns: Int = 0, rows: Int = 0, profile: TelnetProfile = TelnetProfile.PASSIVE, nonBinaryPolicy: NonBinaryPolicy = NonBinaryPolicy.STRICT_ASCII, newlinePolicy: NewlinePolicy = NewlinePolicy.PRESERVE_LF, consumeRecords: Boolean = false, charset: String? = null, maxSubnegotiationBytes: Int = 64 * 1024, inputBudgetBytes: Int = 256 * 1024, outputBudgetBytes: Int = 256 * 1024, maxPendingEvents: Int = 8192, controlBudgetBytes: Int = 32 * 1024, charsetTimeoutMillis: Long, closeTimeoutMillis: Long)](telnetlib/org.connectbot.telnetlib/-telnet-config/-telnet-config.html)

- Added: [val charset: String?](telnetlib/org.connectbot.telnetlib/-telnet-config/charset.html)

- Added: [val charsetTimeoutMillis: Long](telnetlib/org.connectbot.telnetlib/-telnet-config/charset-timeout-millis.html)

- Added: [val closeTimeoutMillis: Long](telnetlib/org.connectbot.telnetlib/-telnet-config/close-timeout-millis.html)

- Added: [val columns: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/columns.html)

- Added: [val consumeRecords: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-config/consume-records.html)

- Added: [val controlBudgetBytes: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/control-budget-bytes.html)

- Added: [val inputBudgetBytes: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/input-budget-bytes.html)

- Added: [val maxPendingEvents: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/max-pending-events.html)

- Added: [val maxSubnegotiationBytes: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/max-subnegotiation-bytes.html)

- Added: [val newlinePolicy: NewlinePolicy](telnetlib/org.connectbot.telnetlib/-telnet-config/newline-policy.html)

- Added: [val nonBinaryPolicy: NonBinaryPolicy](telnetlib/org.connectbot.telnetlib/-telnet-config/non-binary-policy.html)

- Added: [val outputBudgetBytes: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/output-budget-bytes.html)

- Added: [val profile: TelnetProfile](telnetlib/org.connectbot.telnetlib/-telnet-config/profile.html)

- Added: [val rows: Int](telnetlib/org.connectbot.telnetlib/-telnet-config/rows.html)

- Added: [val terminalTypes: List<String>](telnetlib/org.connectbot.telnetlib/-telnet-config/terminal-types.html)

- Added: [class TelnetConfig(terminalTypes: List<String> = listOf("UNKNOWN"), val columns: Int = 0, val rows: Int = 0, val profile: TelnetProfile = TelnetProfile.PASSIVE, val nonBinaryPolicy: NonBinaryPolicy = NonBinaryPolicy.STRICT_ASCII, val newlinePolicy: NewlinePolicy = NewlinePolicy.PRESERVE_LF, val consumeRecords: Boolean = false, val charset: String? = null, val maxSubnegotiationBytes: Int = 64 * 1024, val inputBudgetBytes: Int = 256 * 1024, val outputBudgetBytes: Int = 256 * 1024, val maxPendingEvents: Int = 8192, val controlBudgetBytes: Int = 32 * 1024, val charsetTimeoutMillis: Long, val closeTimeoutMillis: Long)](telnetlib/org.connectbot.telnetlib/-telnet-config/index.html)

#### org.connectbot.telnetlib.TelnetDirection

- Added: [LOCAL](telnetlib/org.connectbot.telnetlib/-telnet-direction/-l-o-c-a-l/index.html)

- Added: [REMOTE](telnetlib/org.connectbot.telnetlib/-telnet-direction/-r-e-m-o-t-e/index.html)

- Added: [enum TelnetDirection : Enum<TelnetDirection> ](telnetlib/org.connectbot.telnetlib/-telnet-direction/index.html)

#### org.connectbot.telnetlib.TelnetEffect

- Added: [sealed interface TelnetEffect](telnetlib/org.connectbot.telnetlib/-telnet-effect/index.html)

#### org.connectbot.telnetlib.TelnetEffect.Input

- Added: [constructor(value: TelnetInput)](telnetlib/org.connectbot.telnetlib/-telnet-effect/-input/-input.html)

- Added: [val value: TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-effect/-input/value.html)

- Added: [data class Input(val value: TelnetInput) : TelnetEffect](telnetlib/org.connectbot.telnetlib/-telnet-effect/-input/index.html)

#### org.connectbot.telnetlib.TelnetEffect.Outbound

- Added: [constructor(bytes: ByteArray)](telnetlib/org.connectbot.telnetlib/-telnet-effect/-outbound/-outbound.html)

- Added: [val bytes: ByteArray](telnetlib/org.connectbot.telnetlib/-telnet-effect/-outbound/bytes.html)

- Added: [data class Outbound(val bytes: ByteArray) : TelnetEffect](telnetlib/org.connectbot.telnetlib/-telnet-effect/-outbound/index.html)

#### org.connectbot.telnetlib.TelnetEffect.StateChanged

- Added: [constructor(snapshot: TelnetSnapshot)](telnetlib/org.connectbot.telnetlib/-telnet-effect/-state-changed/-state-changed.html)

- Added: [val snapshot: TelnetSnapshot](telnetlib/org.connectbot.telnetlib/-telnet-effect/-state-changed/snapshot.html)

- Added: [data class StateChanged(val snapshot: TelnetSnapshot) : TelnetEffect](telnetlib/org.connectbot.telnetlib/-telnet-effect/-state-changed/index.html)

#### org.connectbot.telnetlib.TelnetEngine

- Added: [fun carriageReturn(): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/carriage-return.html)

- Added: [fun feed(buffer: ByteBuffer): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/feed.html)

- Added: [fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/feed.html)

- Added: [fun finish(): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/finish.html)

- Added: [fun flush(): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/flush.html)

- Added: [fun isEnabled(option: TelnetOption, direction: TelnetDirection): Boolean](telnetlib/org.connectbot.telnetlib/-telnet-engine/is-enabled.html)

- Added: [fun requestOption(option: TelnetOption, direction: TelnetDirection, enable: Boolean): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/request-option.html)

- Added: [fun resize(columns: Int, rows: Int): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/resize.html)

- Added: [fun sendCommand(command: TelnetCommand): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/send-command.html)

- Added: [fun start(): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/start.html)

- Added: [fun timeoutCharset(): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/timeout-charset.html)

- Added: [fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): TelnetResult](telnetlib/org.connectbot.telnetlib/-telnet-engine/write.html)

- Added: [object Companion](telnetlib/org.connectbot.telnetlib/-telnet-engine/-companion/index.html)

- Added: [val config: TelnetConfig](telnetlib/org.connectbot.telnetlib/-telnet-engine/config.html)

- Added: [val snapshot: TelnetSnapshot](telnetlib/org.connectbot.telnetlib/-telnet-engine/snapshot.html)

- Added: [class TelnetEngine](telnetlib/org.connectbot.telnetlib/-telnet-engine/index.html)

#### org.connectbot.telnetlib.TelnetEngine.Companion

- Added: [const val CHUNK_SIZE: Int = 8192](telnetlib/org.connectbot.telnetlib/-telnet-engine/-companion/-c-h-u-n-k_-s-i-z-e.html)

- Added: [object Companion](telnetlib/org.connectbot.telnetlib/-telnet-engine/-companion/index.html)

#### org.connectbot.telnetlib.TelnetEngineKt

- Added: [fun createTelnetEngine(config: TelnetConfig = TelnetConfig()): TelnetEngine](telnetlib/org.connectbot.telnetlib/create-telnet-engine.html)

#### org.connectbot.telnetlib.TelnetFailure

- Added: [constructor(code: TelnetFailureCode, message: String)](telnetlib/org.connectbot.telnetlib/-telnet-failure/-telnet-failure.html)

- Added: [val code: TelnetFailureCode](telnetlib/org.connectbot.telnetlib/-telnet-failure/code.html)

- Added: [val message: String](telnetlib/org.connectbot.telnetlib/-telnet-failure/message.html)

- Added: [data class TelnetFailure(val code: TelnetFailureCode, val message: String)](telnetlib/org.connectbot.telnetlib/-telnet-failure/index.html)

#### org.connectbot.telnetlib.TelnetFailureCode

- Added: [BUFFER_LIMIT](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-b-u-f-f-e-r_-l-i-m-i-t/index.html)

- Added: [CHARSET_TIMEOUT](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-c-h-a-r-s-e-t_-t-i-m-e-o-u-t/index.html)

- Added: [CLOSED](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-c-l-o-s-e-d/index.html)

- Added: [CONNECT_TIMEOUT](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-c-o-n-n-e-c-t_-t-i-m-e-o-u-t/index.html)

- Added: [INTERNAL_ERROR](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-i-n-t-e-r-n-a-l_-e-r-r-o-r/index.html)

- Added: [NON_ASCII_DATA](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-n-o-n_-a-s-c-i-i_-d-a-t-a/index.html)

- Added: [PROTOCOL_ERROR](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-p-r-o-t-o-c-o-l_-e-r-r-o-r/index.html)

- Added: [TRANSPORT_ERROR](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-t-r-a-n-s-p-o-r-t_-e-r-r-o-r/index.html)

- Added: [UNSUPPORTED_OPERATION](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/-u-n-s-u-p-p-o-r-t-e-d_-o-p-e-r-a-t-i-o-n/index.html)

- Added: [enum TelnetFailureCode : Enum<TelnetFailureCode> ](telnetlib/org.connectbot.telnetlib/-telnet-failure-code/index.html)

#### org.connectbot.telnetlib.TelnetInput

- Added: [sealed interface TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/index.html)

#### org.connectbot.telnetlib.TelnetInput.CharsetAgreed

- Added: [constructor(name: String)](telnetlib/org.connectbot.telnetlib/-telnet-input/-charset-agreed/-charset-agreed.html)

- Added: [val name: String](telnetlib/org.connectbot.telnetlib/-telnet-input/-charset-agreed/name.html)

- Added: [data class CharsetAgreed(val name: String) : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-charset-agreed/index.html)

#### org.connectbot.telnetlib.TelnetInput.Data

- Added: [constructor(bytes: ByteArray)](telnetlib/org.connectbot.telnetlib/-telnet-input/-data/-data.html)

- Added: [val bytes: ByteArray](telnetlib/org.connectbot.telnetlib/-telnet-input/-data/bytes.html)

- Added: [data class Data(val bytes: ByteArray) : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-data/index.html)

#### org.connectbot.telnetlib.TelnetInput.Diagnostic

- Added: [constructor(message: String)](telnetlib/org.connectbot.telnetlib/-telnet-input/-diagnostic/-diagnostic.html)

- Added: [val message: String](telnetlib/org.connectbot.telnetlib/-telnet-input/-diagnostic/message.html)

- Added: [data class Diagnostic(val message: String) : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-diagnostic/index.html)

#### org.connectbot.telnetlib.TelnetInput.EchoChanged

- Added: [constructor(localEcho: Boolean)](telnetlib/org.connectbot.telnetlib/-telnet-input/-echo-changed/-echo-changed.html)

- Added: [val localEcho: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-input/-echo-changed/local-echo.html)

- Added: [data class EchoChanged(val localEcho: Boolean) : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-echo-changed/index.html)

#### org.connectbot.telnetlib.TelnetInput.EndOfRecord

- Added: [data object EndOfRecord : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-end-of-record/index.html)

#### org.connectbot.telnetlib.TelnetInput.GoAhead

- Added: [data object GoAhead : TelnetInput](telnetlib/org.connectbot.telnetlib/-telnet-input/-go-ahead/index.html)

#### org.connectbot.telnetlib.TelnetOption

- Added: [BINARY](telnetlib/org.connectbot.telnetlib/-telnet-option/-b-i-n-a-r-y/index.html)

- Added: [CHARSET](telnetlib/org.connectbot.telnetlib/-telnet-option/-c-h-a-r-s-e-t/index.html)

- Added: [ECHO](telnetlib/org.connectbot.telnetlib/-telnet-option/-e-c-h-o/index.html)

- Added: [EOR](telnetlib/org.connectbot.telnetlib/-telnet-option/-e-o-r/index.html)

- Added: [NAWS](telnetlib/org.connectbot.telnetlib/-telnet-option/-n-a-w-s/index.html)

- Added: [SGA](telnetlib/org.connectbot.telnetlib/-telnet-option/-s-g-a/index.html)

- Added: [TTYPE](telnetlib/org.connectbot.telnetlib/-telnet-option/-t-t-y-p-e/index.html)

- Added: [val code: Int](telnetlib/org.connectbot.telnetlib/-telnet-option/code.html)

- Added: [enum TelnetOption : Enum<TelnetOption> ](telnetlib/org.connectbot.telnetlib/-telnet-option/index.html)

#### org.connectbot.telnetlib.TelnetOutcome

- Added: [sealed interface TelnetOutcome<out T>](telnetlib/org.connectbot.telnetlib/-telnet-outcome/index.html)

#### org.connectbot.telnetlib.TelnetOutcome.Failure

- Added: [constructor(error: TelnetFailure)](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-failure/-failure.html)

- Added: [val error: TelnetFailure](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-failure/error.html)

- Added: [data class Failure(val error: TelnetFailure) : TelnetOutcome<Nothing> ](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-failure/index.html)

#### org.connectbot.telnetlib.TelnetOutcome.Success

- Added: [constructor(value: T)](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-success/-success.html)

- Added: [val value: T](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-success/value.html)

- Added: [data class Success<T>(val value: T) : TelnetOutcome<T> ](telnetlib/org.connectbot.telnetlib/-telnet-outcome/-success/index.html)

#### org.connectbot.telnetlib.TelnetProfile

- Added: [INTERACTIVE](telnetlib/org.connectbot.telnetlib/-telnet-profile/-i-n-t-e-r-a-c-t-i-v-e/index.html)

- Added: [PASSIVE](telnetlib/org.connectbot.telnetlib/-telnet-profile/-p-a-s-s-i-v-e/index.html)

- Added: [enum TelnetProfile : Enum<TelnetProfile> ](telnetlib/org.connectbot.telnetlib/-telnet-profile/index.html)

#### org.connectbot.telnetlib.TelnetResult

- Added: [constructor(consumed: Int, effects: List<TelnetEffect>, failure: TelnetFailure? = null)](telnetlib/org.connectbot.telnetlib/-telnet-result/-telnet-result.html)

- Added: [val consumed: Int](telnetlib/org.connectbot.telnetlib/-telnet-result/consumed.html)

- Added: [val effects: List<TelnetEffect>](telnetlib/org.connectbot.telnetlib/-telnet-result/effects.html)

- Added: [val failure: TelnetFailure?](telnetlib/org.connectbot.telnetlib/-telnet-result/failure.html)

- Added: [data class TelnetResult(val consumed: Int, val effects: List<TelnetEffect>, val failure: TelnetFailure? = null)](telnetlib/org.connectbot.telnetlib/-telnet-result/index.html)

#### org.connectbot.telnetlib.TelnetSession

- Added: [abstract suspend fun carriageReturn(): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/carriage-return.html)

- Added: [abstract suspend fun close(): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/close.html)

- Added: [abstract suspend fun flush(): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/flush.html)

- Added: [open fun getLocalSocketAddress(): InetSocketAddress?](telnetlib/org.connectbot.telnetlib/-telnet-session/get-local-socket-address.html)

- Added: [abstract suspend fun receive(): TelnetOutcome<TelnetInput?>](telnetlib/org.connectbot.telnetlib/-telnet-session/receive.html)

- Added: [abstract suspend fun requestOption(option: TelnetOption, direction: TelnetDirection, enable: Boolean): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/request-option.html)

- Added: [abstract suspend fun resize(columns: Int, rows: Int): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/resize.html)

- Added: [abstract suspend fun sendCommand(command: TelnetCommand): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/send-command.html)

- Added: [abstract suspend fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): TelnetOutcome<Unit>](telnetlib/org.connectbot.telnetlib/-telnet-session/write.html)

- Added: [abstract val state: StateFlow<TelnetSnapshot>](telnetlib/org.connectbot.telnetlib/-telnet-session/state.html)

- Added: [interface TelnetSession](telnetlib/org.connectbot.telnetlib/-telnet-session/index.html)

#### org.connectbot.telnetlib.TelnetSessionKt

- Added: [fun openTelnetSession(scope: CoroutineScope, transport: TelnetTransport, config: TelnetConfig = TelnetConfig(), workerContext: CoroutineContext = scope.coroutineContext.minusKey(Job)): TelnetSession](telnetlib/org.connectbot.telnetlib/open-telnet-session.html)

#### org.connectbot.telnetlib.TelnetSnapshot

- Added: [constructor(status: TelnetStatus = TelnetStatus.OPEN, localEcho: Boolean = true, incomingBinary: Boolean = false, outgoingBinary: Boolean = false, charset: String? = null, charsetPending: Boolean = false, failure: TelnetFailure? = null)](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/-telnet-snapshot.html)

- Added: [val charset: String?](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/charset.html)

- Added: [val charsetPending: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/charset-pending.html)

- Added: [val failure: TelnetFailure?](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/failure.html)

- Added: [val incomingBinary: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/incoming-binary.html)

- Added: [val localEcho: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/local-echo.html)

- Added: [val outgoingBinary: Boolean](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/outgoing-binary.html)

- Added: [val status: TelnetStatus](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/status.html)

- Added: [data class TelnetSnapshot(val status: TelnetStatus = TelnetStatus.OPEN, val localEcho: Boolean = true, val incomingBinary: Boolean = false, val outgoingBinary: Boolean = false, val charset: String? = null, val charsetPending: Boolean = false, val failure: TelnetFailure? = null)](telnetlib/org.connectbot.telnetlib/-telnet-snapshot/index.html)

#### org.connectbot.telnetlib.TelnetStatus

- Added: [CANCELLED](telnetlib/org.connectbot.telnetlib/-telnet-status/-c-a-n-c-e-l-l-e-d/index.html)

- Added: [FAILED](telnetlib/org.connectbot.telnetlib/-telnet-status/-f-a-i-l-e-d/index.html)

- Added: [LOCAL_CLOSED](telnetlib/org.connectbot.telnetlib/-telnet-status/-l-o-c-a-l_-c-l-o-s-e-d/index.html)

- Added: [OPEN](telnetlib/org.connectbot.telnetlib/-telnet-status/-o-p-e-n/index.html)

- Added: [PEER_EOF](telnetlib/org.connectbot.telnetlib/-telnet-status/-p-e-e-r_-e-o-f/index.html)

- Added: [enum TelnetStatus : Enum<TelnetStatus> ](telnetlib/org.connectbot.telnetlib/-telnet-status/index.html)

#### org.connectbot.telnetlib.TelnetTransport

- Added: [abstract suspend fun close()](telnetlib/org.connectbot.telnetlib/-telnet-transport/close.html)

- Added: [abstract suspend fun flush()](telnetlib/org.connectbot.telnetlib/-telnet-transport/flush.html)

- Added: [open fun getLocalSocketAddress(): InetSocketAddress?](telnetlib/org.connectbot.telnetlib/-telnet-transport/get-local-socket-address.html)

- Added: [abstract suspend fun read(destination: ByteArray, offset: Int, length: Int): Int](telnetlib/org.connectbot.telnetlib/-telnet-transport/read.html)

- Added: [abstract suspend fun write(source: ByteArray, offset: Int, length: Int)](telnetlib/org.connectbot.telnetlib/-telnet-transport/write.html)

- Added: [interface TelnetTransport](telnetlib/org.connectbot.telnetlib/-telnet-transport/index.html)

### Ktor backend

#### org.connectbot.telnetlib.ktor.KtorTelnetKt

- Added: [suspend fun connectTelnet(scope: CoroutineScope, host: String, port: Int = 23, config: TelnetConfig = TelnetConfig(), ioContext: CoroutineContext = Dispatchers.IO, connectTimeoutMillis: Long): TelnetOutcome<TelnetSession>](telnetlib-ktor/org.connectbot.telnetlib.ktor/connect-telnet.html)
<!-- END DOCS API CHANGES -->
