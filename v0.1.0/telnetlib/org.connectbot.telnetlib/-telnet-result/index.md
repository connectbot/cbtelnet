//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetResult](index.md)

# TelnetResult

[jvm]\
data class [TelnetResult](index.md)(val consumed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), val effects: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[TelnetEffect](../-telnet-effect/index.md)&gt;, val failure: [TelnetFailure](../-telnet-failure/index.md)? = null)

A bounded processing step, including complete effects preceding any failure.

## Constructors

| | |
|---|---|
| [TelnetResult](-telnet-result.md) | [jvm]<br>constructor(consumed: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), effects: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[TelnetEffect](../-telnet-effect/index.md)&gt;, failure: [TelnetFailure](../-telnet-failure/index.md)? = null) |

## Properties

| Name | Summary |
|---|---|
| [consumed](consumed.md) | [jvm]<br>val [consumed](consumed.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [effects](effects.md) | [jvm]<br>val [effects](effects.md): [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[TelnetEffect](../-telnet-effect/index.md)&gt; |
| [failure](failure.md) | [jvm]<br>val [failure](failure.md): [TelnetFailure](../-telnet-failure/index.md)? |
