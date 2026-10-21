//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetSession](index.md)/[resize](resize.md)

# resize

[jvm]\
abstract suspend fun [resize](resize.md)(columns: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), rows: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt;

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | when either dimension is outside 0..65535. |
