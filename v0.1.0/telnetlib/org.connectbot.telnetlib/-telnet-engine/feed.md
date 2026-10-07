//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetEngine](index.md)/[feed](feed.md)

# feed

[jvm]\
fun [feed](feed.md)(bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = bytes.size - offset): [TelnetResult](../-telnet-result/index.md)

[jvm]\
fun [feed](feed.md)(buffer: [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)): [TelnetResult](../-telnet-result/index.md)

Advances position by consumed bytes; never changes limit or retains the buffer.
