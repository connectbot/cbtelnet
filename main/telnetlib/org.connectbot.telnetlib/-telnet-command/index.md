//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetCommand](index.md)

# TelnetCommand

[jvm]\
enum [TelnetCommand](index.md) : [Enum](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-enum/index.html)&lt;[TelnetCommand](index.md)&gt; 

In-band commands. TCP urgent-data SYNCH is deliberately not represented.

## Entries

| | |
|---|---|
| [NOP](-n-o-p/index.md) | [jvm]<br>[NOP](-n-o-p/index.md) |
| [DATA_MARK](-d-a-t-a_-m-a-r-k/index.md) | [jvm]<br>[DATA_MARK](-d-a-t-a_-m-a-r-k/index.md) |
| [BREAK](-b-r-e-a-k/index.md) | [jvm]<br>[BREAK](-b-r-e-a-k/index.md) |
| [INTERRUPT_PROCESS](-i-n-t-e-r-r-u-p-t_-p-r-o-c-e-s-s/index.md) | [jvm]<br>[INTERRUPT_PROCESS](-i-n-t-e-r-r-u-p-t_-p-r-o-c-e-s-s/index.md) |
| [ABORT_OUTPUT](-a-b-o-r-t_-o-u-t-p-u-t/index.md) | [jvm]<br>[ABORT_OUTPUT](-a-b-o-r-t_-o-u-t-p-u-t/index.md) |
| [ARE_YOU_THERE](-a-r-e_-y-o-u_-t-h-e-r-e/index.md) | [jvm]<br>[ARE_YOU_THERE](-a-r-e_-y-o-u_-t-h-e-r-e/index.md) |
| [ERASE_CHARACTER](-e-r-a-s-e_-c-h-a-r-a-c-t-e-r/index.md) | [jvm]<br>[ERASE_CHARACTER](-e-r-a-s-e_-c-h-a-r-a-c-t-e-r/index.md) |
| [ERASE_LINE](-e-r-a-s-e_-l-i-n-e/index.md) | [jvm]<br>[ERASE_LINE](-e-r-a-s-e_-l-i-n-e/index.md) |
| [GO_AHEAD](-g-o_-a-h-e-a-d/index.md) | [jvm]<br>[GO_AHEAD](-g-o_-a-h-e-a-d/index.md) |

## Properties

| Name | Summary |
|---|---|
| [code](code.md) | [jvm]<br>val [code](code.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [entries](entries.md) | [jvm]<br>val [entries](entries.md): [EnumEntries](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.enums/-enum-entries/index.html)&lt;[TelnetCommand](index.md)&gt;<br>Returns a representation of an immutable list of all enum entries, in the order they're declared. |
| [name](../-telnet-failure-code/-i-n-t-e-r-n-a-l_-e-r-r-o-r/index.md#-372974862%2FProperties%2F-1317821935) | [jvm]<br>val [name](../-telnet-failure-code/-i-n-t-e-r-n-a-l_-e-r-r-o-r/index.md#-372974862%2FProperties%2F-1317821935): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) |
| [ordinal](../-telnet-failure-code/-i-n-t-e-r-n-a-l_-e-r-r-o-r/index.md#-739389684%2FProperties%2F-1317821935) | [jvm]<br>val [ordinal](../-telnet-failure-code/-i-n-t-e-r-n-a-l_-e-r-r-o-r/index.md#-739389684%2FProperties%2F-1317821935): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [valueOf](value-of.md) | [jvm]<br>fun [valueOf](value-of.md)(value: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)): [TelnetCommand](index.md)<br>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.) |
| [values](values.md) | [jvm]<br>fun [values](values.md)(): [Array](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-array/index.html)&lt;[TelnetCommand](index.md)&gt;<br>Returns an array containing the constants of this enum type, in the order they're declared. |
