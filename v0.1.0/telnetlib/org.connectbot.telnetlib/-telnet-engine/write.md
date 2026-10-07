//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetEngine](index.md)/[write](write.md)

# write

[jvm]\
fun [write](write.md)(bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = bytes.size - offset): [TelnetResult](../-telnet-result/index.md)

Encodes one bounded application slice. A trailing raw CR is paired by the next write or flush.
