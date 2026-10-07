//[ConnectBot Telnet Library](../../index.md)/[org.connectbot.telnetlib](../index.md)/[TelnetConfig](index.md)

# TelnetConfig

class [TelnetConfig](index.md)(terminalTypes: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)&gt; = listOf(&quot;UNKNOWN&quot;), val columns: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val rows: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, val profile: [TelnetProfile](../-telnet-profile/index.md) = TelnetProfile.PASSIVE, val nonBinaryPolicy: [NonBinaryPolicy](../-non-binary-policy/index.md) = NonBinaryPolicy.STRICT_ASCII, val newlinePolicy: [NewlinePolicy](../-newline-policy/index.md) = NewlinePolicy.PRESERVE_LF, val consumeRecords: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, val charset: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? = null, val maxSubnegotiationBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 64 * 1024, val inputBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 256 * 1024, val outputBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 256 * 1024, val maxPendingEvents: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8192, val controlBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 32 * 1024, val charsetTimeoutMillis: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html), val closeTimeoutMillis: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html))

Immutable protocol settings. Terminal capabilities must match the actual emulator.

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-illegal-argument-exception/index.html) | for invalid capabilities, dimensions, budgets or deadlines. |

## Constructors

| | |
|---|---|
| [TelnetConfig](-telnet-config.md) | [jvm]<br>constructor(terminalTypes: [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)&gt; = listOf(&quot;UNKNOWN&quot;), columns: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, rows: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 0, profile: [TelnetProfile](../-telnet-profile/index.md) = TelnetProfile.PASSIVE, nonBinaryPolicy: [NonBinaryPolicy](../-non-binary-policy/index.md) = NonBinaryPolicy.STRICT_ASCII, newlinePolicy: [NewlinePolicy](../-newline-policy/index.md) = NewlinePolicy.PRESERVE_LF, consumeRecords: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = false, charset: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? = null, maxSubnegotiationBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 64 * 1024, inputBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 256 * 1024, outputBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 256 * 1024, maxPendingEvents: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 8192, controlBudgetBytes: [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) = 32 * 1024, charsetTimeoutMillis: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html), closeTimeoutMillis: [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [charset](charset.md) | [jvm]<br>val [charset](charset.md): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)? |
| [charsetTimeoutMillis](charset-timeout-millis.md) | [jvm]<br>val [charsetTimeoutMillis](charset-timeout-millis.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [closeTimeoutMillis](close-timeout-millis.md) | [jvm]<br>val [closeTimeoutMillis](close-timeout-millis.md): [Long](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-long/index.html) |
| [columns](columns.md) | [jvm]<br>val [columns](columns.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [consumeRecords](consume-records.md) | [jvm]<br>val [consumeRecords](consume-records.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) |
| [controlBudgetBytes](control-budget-bytes.md) | [jvm]<br>val [controlBudgetBytes](control-budget-bytes.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [inputBudgetBytes](input-budget-bytes.md) | [jvm]<br>val [inputBudgetBytes](input-budget-bytes.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [maxPendingEvents](max-pending-events.md) | [jvm]<br>val [maxPendingEvents](max-pending-events.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [maxSubnegotiationBytes](max-subnegotiation-bytes.md) | [jvm]<br>val [maxSubnegotiationBytes](max-subnegotiation-bytes.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [newlinePolicy](newline-policy.md) | [jvm]<br>val [newlinePolicy](newline-policy.md): [NewlinePolicy](../-newline-policy/index.md) |
| [nonBinaryPolicy](non-binary-policy.md) | [jvm]<br>val [nonBinaryPolicy](non-binary-policy.md): [NonBinaryPolicy](../-non-binary-policy/index.md) |
| [outputBudgetBytes](output-budget-bytes.md) | [jvm]<br>val [outputBudgetBytes](output-budget-bytes.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [profile](profile.md) | [jvm]<br>val [profile](profile.md): [TelnetProfile](../-telnet-profile/index.md) |
| [rows](rows.md) | [jvm]<br>val [rows](rows.md): [Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html) |
| [terminalTypes](terminal-types.md) | [jvm]<br>val [terminalTypes](terminal-types.md): [List](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)&gt; |
