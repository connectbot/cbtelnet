//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetSession](index.md)

# TelnetSession

[jvm]\
interface [TelnetSession](index.md)

Ordered, single-consumer input and concurrent suspend output operations.

## Properties

| Name | Summary |
|---|---|
| [state](state.md) | [jvm]<br>abstract val [state](state.md): StateFlow&lt;[TelnetSnapshot](../-telnet-snapshot/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [carriageReturn](carriage-return.md) | [jvm]<br>abstract suspend fun [carriageReturn](carriage-return.md)(): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [close](close.md) | [jvm]<br>abstract suspend fun [close](close.md)(): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [flush](flush.md) | [jvm]<br>abstract suspend fun [flush](flush.md)(): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [getLocalSocketAddress](get-local-socket-address.md) | [jvm]<br>open fun [getLocalSocketAddress](get-local-socket-address.md)(): [InetSocketAddress](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/InetSocketAddress.html)?<br>Local socket endpoint captured when the session opens. |
| [receive](receive.md) | [jvm]<br>abstract suspend fun [receive](receive.md)(): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[TelnetInput](../-telnet-input/index.md)?&gt; |
| [requestOption](request-option.md) | [jvm]<br>abstract suspend fun [requestOption](request-option.md)(option: [TelnetOption](../-telnet-option/index.md), direction: [TelnetDirection](../-telnet-direction/index.md), enable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [resize](resize.md) | [jvm]<br>abstract suspend fun [resize](resize.md)(columns: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html), rows: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [sendCommand](send-command.md) | [jvm]<br>abstract suspend fun [sendCommand](send-command.md)(command: [TelnetCommand](../-telnet-command/index.md)): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
| [write](write.md) | [jvm]<br>abstract suspend fun [write](write.md)(data: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html), offset: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, length: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = data.size - offset): [TelnetOutcome](../-telnet-outcome/index.md)&lt;[Unit](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-unit/index.html)&gt; |
