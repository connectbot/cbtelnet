//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetSession](index.md)/[receive](receive.md)

# receive

[jvm]\
abstract suspend fun [receive](receive.md)(): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[TelnetInput](../-telnet-input/index.md)?&gt;

#### Throws

| | |
|---|---|
| [IllegalStateException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-state-exception/index.html) | when another receive call is in progress. |
