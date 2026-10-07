//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetSnapshot](index.md)

# TelnetSnapshot

[jvm]\
data class [TelnetSnapshot](index.md)(val status: [TelnetStatus](../-telnet-status/index.md) = TelnetStatus.OPEN, val localEcho: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true, val incomingBinary: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val outgoingBinary: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val charset: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? = null, val charsetPending: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val failure: [TelnetFailure](../-telnet-failure/index.md)? = null)

## Constructors

| | |
|---|---|
| [TelnetSnapshot](-telnet-snapshot.md) | [jvm]<br>constructor(status: [TelnetStatus](../-telnet-status/index.md) = TelnetStatus.OPEN, localEcho: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true, incomingBinary: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, outgoingBinary: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, charset: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? = null, charsetPending: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, failure: [TelnetFailure](../-telnet-failure/index.md)? = null) |

## Properties

| Name | Summary |
|---|---|
| [charset](charset.md) | [jvm]<br>val [charset](charset.md): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? |
| [charsetPending](charset-pending.md) | [jvm]<br>val [charsetPending](charset-pending.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [failure](failure.md) | [jvm]<br>val [failure](failure.md): [TelnetFailure](../-telnet-failure/index.md)? |
| [incomingBinary](incoming-binary.md) | [jvm]<br>val [incomingBinary](incoming-binary.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [localEcho](local-echo.md) | [jvm]<br>val [localEcho](local-echo.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [outgoingBinary](outgoing-binary.md) | [jvm]<br>val [outgoingBinary](outgoing-binary.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [status](status.md) | [jvm]<br>val [status](status.md): [TelnetStatus](../-telnet-status/index.md) |
