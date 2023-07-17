//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.state](../index.md)/[VpnStateMonitor](index.md)

# VpnStateMonitor

[androidJvm]\
interface [VpnStateMonitor](index.md)

## Types

| Name | Summary |
|---|---|
| [AlwaysOnState](-always-on-state/index.md) | [androidJvm]<br>data class [AlwaysOnState](-always-on-state/index.md)(val enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), val lockedDown: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)) |
| [VpnRunningState](-vpn-running-state/index.md) | [androidJvm]<br>enum [VpnRunningState](-vpn-running-state/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnStateMonitor.VpnRunningState](-vpn-running-state/index.md)&gt; |
| [VpnState](-vpn-state/index.md) | [androidJvm]<br>data class [VpnState](-vpn-state/index.md)(val state: [VpnStateMonitor.VpnRunningState](-vpn-running-state/index.md), val stopReason: [VpnStateMonitor.VpnStopReason](-vpn-stop-reason/index.md)? = null, val alwaysOnState: [VpnStateMonitor.AlwaysOnState](-always-on-state/index.md) = DEFAULT) |
| [VpnStopReason](-vpn-stop-reason/index.md) | [androidJvm]<br>enum [VpnStopReason](-vpn-stop-reason/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnStateMonitor.VpnStopReason](-vpn-stop-reason/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [getStateFlow](get-state-flow.md) | [androidJvm]<br>abstract fun [getStateFlow](get-state-flow.md)(vpnFeature: [VpnFeature](../../com.duckduckgo.mobile.android.vpn/-vpn-feature/index.md)): Flow&lt;[VpnStateMonitor.VpnState](-vpn-state/index.md)&gt;<br>Returns a flow of VPN changes for the given [vpnFeature](get-state-flow.md) It follows the following truth table: |
| [isAlwaysOnEnabled](is-always-on-enabled.md) | [androidJvm]<br>abstract suspend fun [isAlwaysOnEnabled](is-always-on-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [vpnLastDisabledByAndroid](vpn-last-disabled-by-android.md) | [androidJvm]<br>abstract suspend fun [vpnLastDisabledByAndroid](vpn-last-disabled-by-android.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
