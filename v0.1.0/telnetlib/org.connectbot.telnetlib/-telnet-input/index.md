//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetInput](index.md)

# TelnetInput

sealed interface [TelnetInput](index.md)

Lossless, ordered application input; each Data owns its array.

#### Inheritors

| |
|---|
| [Data](-data/index.md) |
| [EndOfRecord](-end-of-record/index.md) |
| [GoAhead](-go-ahead/index.md) |
| [EchoChanged](-echo-changed/index.md) |
| [CharsetAgreed](-charset-agreed/index.md) |
| [Diagnostic](-diagnostic/index.md) |

## Types

| Name | Summary |
|---|---|
| [CharsetAgreed](-charset-agreed/index.md) | [jvm]<br>data class [CharsetAgreed](-charset-agreed/index.md)(val name: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) : [TelnetInput](index.md) |
| [Data](-data/index.md) | [jvm]<br>data class [Data](-data/index.md)(val bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) : [TelnetInput](index.md) |
| [Diagnostic](-diagnostic/index.md) | [jvm]<br>data class [Diagnostic](-diagnostic/index.md)(val message: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)) : [TelnetInput](index.md) |
| [EchoChanged](-echo-changed/index.md) | [jvm]<br>data class [EchoChanged](-echo-changed/index.md)(val localEcho: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)) : [TelnetInput](index.md) |
| [EndOfRecord](-end-of-record/index.md) | [jvm]<br>data object [EndOfRecord](-end-of-record/index.md) : [TelnetInput](index.md) |
| [GoAhead](-go-ahead/index.md) | [jvm]<br>data object [GoAhead](-go-ahead/index.md) : [TelnetInput](index.md) |
