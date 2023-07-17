//[network-protection-api](../../../index.md)/[com.duckduckgo.networkprotection.api](../index.md)/[NetworkProtectionState](index.md)/[isRunning](is-running.md)

# isRunning

[androidJvm]\
abstract suspend fun [isRunning](is-running.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This is a suspend function because the operation can take time You DO NOT need to set any dispatcher to call this suspend function

#### Return

`true` when NetP is enabled AND the VPN is running, `false` otherwise
