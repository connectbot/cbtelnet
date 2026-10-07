//[ConnectBot Telnet Library](../index.md)/[org.connectbot.telnetlib](index.md)/[openTelnetSession](open-telnet-session.md)

# openTelnetSession

[jvm]\
fun [openTelnetSession](open-telnet-session.md)(scope: CoroutineScope, transport: [TelnetTransport](-telnet-transport/index.md), config: [TelnetConfig](-telnet-config/index.md) = TelnetConfig(), workerContext: [CoroutineContext](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-coroutine-context/index.html) = scope.coroutineContext.minusKey(Job)): [TelnetSession](-telnet-session/index.md)

Opens a child session over an already connected transport. The session owns and closes that transport. Each large write is accepted in independent bounded chunks, allowing negotiation and competing operations between chunks.
