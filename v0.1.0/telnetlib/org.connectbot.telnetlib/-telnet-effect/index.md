//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetEffect](index.md)

# TelnetEffect

sealed interface [TelnetEffect](index.md)

An ordered effect. Outbound arrays are complete frames owned by their recipient.

#### Inheritors

| |
|---|
| [Input](-input/index.md) |
| [Outbound](-outbound/index.md) |
| [StateChanged](-state-changed/index.md) |

## Types

| Name | Summary |
|---|---|
| [Input](-input/index.md) | [jvm]<br>data class [Input](-input/index.md)(val value: [TelnetInput](../-telnet-input/index.md)) : [TelnetEffect](index.md) |
| [Outbound](-outbound/index.md) | [jvm]<br>data class [Outbound](-outbound/index.md)(val bytes: [ByteArray](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-byte-array/index.html)) : [TelnetEffect](index.md) |
| [StateChanged](-state-changed/index.md) | [jvm]<br>data class [StateChanged](-state-changed/index.md)(val snapshot: [TelnetSnapshot](../-telnet-snapshot/index.md)) : [TelnetEffect](index.md) |
