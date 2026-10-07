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