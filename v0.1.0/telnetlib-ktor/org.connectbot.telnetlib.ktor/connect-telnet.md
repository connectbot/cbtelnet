//[ConnectBot Telnet Ktor Backend](../index.md)/[org.connectbot.telnetlib.ktor](index.md)/[connectTelnet](connect-telnet.md)

# connectTelnet

[jvm]\
suspend fun [connectTelnet](connect-telnet.md)(scope: CoroutineScope, host: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html), port: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 23, config: [TelnetConfig](../../telnetlib/org.connectbot.telnetlib/-telnet-config/index.md) = TelnetConfig(), ioContext: [CoroutineContext](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-coroutine-context/index.html) = Dispatchers.IO, connectTimeoutMillis: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)): [TelnetOutcome](../../telnetlib/org.connectbot.telnetlib/-telnet-outcome/index.md)&lt;[TelnetSession](../../telnetlib/org.connectbot.telnetlib/-telnet-session/index.md)&gt;

Connects a child Telnet session using Ktor TCP. The session owns its socket and selector; cancellation during connect also closes both. No Java Socket API is used.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | for a blank host, invalid port or nonpositive timeout. |
