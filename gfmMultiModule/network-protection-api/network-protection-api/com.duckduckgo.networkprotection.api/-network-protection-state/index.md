//[network-protection-api](../../../index.md)/[com.duckduckgo.networkprotection.api](../index.md)/[NetworkProtectionState](index.md)

# NetworkProtectionState

[androidJvm]\
interface [NetworkProtectionState](index.md)

## Functions

| Name | Summary |
|---|---|
| [isEnabled](is-enabled.md) | [androidJvm]<br>abstract suspend fun [isEnabled](is-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This is a suspend function because the operation can take time. You DO NOT need to set any dispatcher to call this suspend function |
| [isRunning](is-running.md) | [androidJvm]<br>abstract suspend fun [isRunning](is-running.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This is a suspend function because the operation can take time You DO NOT need to set any dispatcher to call this suspend function |
| [restart](restart.md) | [androidJvm]<br>abstract fun [restart](restart.md)()<br>This method will restart the App Tracking Protection feature by disabling it and re-enabling back again |
