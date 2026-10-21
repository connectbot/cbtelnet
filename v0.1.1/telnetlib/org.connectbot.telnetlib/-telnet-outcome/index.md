//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetOutcome](index.md)

# TelnetOutcome

sealed interface [TelnetOutcome](index.md)&lt;out [T](index.md)&gt;

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [Failure](-failure/index.md) |

## Types

| Name | Summary |
|---|---|
| [Failure](-failure/index.md) | [jvm]<br>data class [Failure](-failure/index.md)(val error: [TelnetFailure](../-telnet-failure/index.md)) : [TelnetOutcome](index.md)&lt;[Nothing](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-nothing/index.html)&gt; |
| [Success](-success/index.md) | [jvm]<br>data class [Success](-success/index.md)&lt;[T](-success/index.md)&gt;(val value: [T](-success/index.md)) : [TelnetOutcome](index.md)&lt;[T](-success/index.md)&gt; |
