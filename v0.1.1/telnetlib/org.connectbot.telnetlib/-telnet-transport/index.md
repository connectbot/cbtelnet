//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetTransport](index.md)

# TelnetTransport

[jvm]\
interface [TelnetTransport](index.md)

A duplex byte transport. Positive reads make progress; -1 is EOF, zero is only legal for a zero-length request. Write must finish the entire slice. Close must promptly interrupt a suspended read/write and be safe to call repeatedly.

## Functions

| Name | Summary |
|---|---|
| [close](close.md) | [jvm]<br>abstract suspend fun [close](close.md)() |
| [flush](flush.md) | [jvm]<br>abstract suspend fun [flush](flush.md)() |
| [getLocalSocketAddress](get-local-socket-address.md) | [jvm]<br>open fun [getLocalSocketAddress](get-local-socket-address.md)(): [InetSocketAddress](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/InetSocketAddress.html)?<br>Local socket endpoint, or null when this transport has no socket metadata. |
| [read](read.md) | [jvm]<br>abstract suspend fun [read](read.md)(destination: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [write](write.md) | [jvm]<br>abstract suspend fun [write](write.md)(source: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)) |
