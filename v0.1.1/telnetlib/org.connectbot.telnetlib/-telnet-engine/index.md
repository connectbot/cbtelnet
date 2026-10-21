//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetEngine](index.md)

# TelnetEngine

[jvm]\
class [TelnetEngine](index.md)

Deterministic, single-owner client engine. No sockets, callbacks, or coroutine state. Feed at most [CHUNK_SIZE](-companion/-c-h-u-n-k_-s-i-z-e.md) bytes per step; repeat with the unconsumed suffix. A result consumes input or reports a terminal failure; empty input is a no-op. Call [start](start.md) once before other operations and [finish](finish.md) once at peer EOF. Invalid slices, dimensions and oversized writes throw IllegalArgumentException. Starting twice or using an unstarted engine throws IllegalStateException.

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [jvm]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [config](config.md) | [jvm]<br>val [config](config.md): [TelnetConfig](../-telnet-config/index.md) |
| [snapshot](snapshot.md) | [jvm]<br>val [snapshot](snapshot.md): [TelnetSnapshot](../-telnet-snapshot/index.md) |

## Functions

| Name | Summary |
|---|---|
| [carriageReturn](carriage-return.md) | [jvm]<br>fun [carriageReturn](carriage-return.md)(): [TelnetResult](../-telnet-result/index.md) |
| [feed](feed.md) | [jvm]<br>fun [feed](feed.md)(buffer: [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)): [TelnetResult](../-telnet-result/index.md)<br>Advances position by consumed bytes; never changes limit or retains the buffer.<br>[jvm]<br>fun [feed](feed.md)(bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = bytes.size - offset): [TelnetResult](../-telnet-result/index.md) |
| [finish](finish.md) | [jvm]<br>fun [finish](finish.md)(): [TelnetResult](../-telnet-result/index.md)<br>Completes valid data and diagnoses a truncated control without dispatching partial SB. |
| [flush](flush.md) | [jvm]<br>fun [flush](flush.md)(): [TelnetResult](../-telnet-result/index.md) |
| [isEnabled](is-enabled.md) | [jvm]<br>fun [isEnabled](is-enabled.md)(option: [TelnetOption](../-telnet-option/index.md), direction: [TelnetDirection](../-telnet-direction/index.md)): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [requestOption](request-option.md) | [jvm]<br>fun [requestOption](request-option.md)(option: [TelnetOption](../-telnet-option/index.md), direction: [TelnetDirection](../-telnet-direction/index.md), enable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [TelnetResult](../-telnet-result/index.md) |
| [resize](resize.md) | [jvm]<br>fun [resize](resize.md)(columns: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), rows: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [TelnetResult](../-telnet-result/index.md) |
| [sendCommand](send-command.md) | [jvm]<br>fun [sendCommand](send-command.md)(command: [TelnetCommand](../-telnet-command/index.md)): [TelnetResult](../-telnet-result/index.md) |
| [start](start.md) | [jvm]<br>fun [start](start.md)(): [TelnetResult](../-telnet-result/index.md) |
| [timeoutCharset](timeout-charset.md) | [jvm]<br>fun [timeoutCharset](timeout-charset.md)(): [TelnetResult](../-telnet-result/index.md) |
| [write](write.md) | [jvm]<br>fun [write](write.md)(bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = bytes.size - offset): [TelnetResult](../-telnet-result/index.md)<br>Encodes one bounded application slice. A trailing raw CR is paired by the next write or flush. |
