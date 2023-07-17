//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../../index.md)/[VpnNetworkStack](../index.md)/[EmptyVpnNetworkStack](index.md)

# EmptyVpnNetworkStack

[androidJvm]\
object [EmptyVpnNetworkStack](index.md) : [VpnNetworkStack](../index.md)

## Properties

| Name | Summary |
|---|---|
| [name](name.md) | [androidJvm]<br>open override val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Name of the networking layer |

## Functions

| Name | Summary |
|---|---|
| [onCreateVpn](on-create-vpn.md) | [androidJvm]<br>open override fun [onCreateVpn](on-create-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt; |
| [onDestroyVpn](on-destroy-vpn.md) | [androidJvm]<br>open override fun [onDestroyVpn](on-destroy-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Clean when the networking layer is destroyed. You can use this method to clean up resources |
| [onPrepareVpn](on-prepare-vpn.md) | [androidJvm]<br>open suspend override fun [onPrepareVpn](on-prepare-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[VpnNetworkStack.VpnTunnelConfig](../-vpn-tunnel-config/index.md)&gt;<br>Called before the vpn tunnel is created and before the vpn is started. |
| [onStartVpn](on-start-vpn.md) | [androidJvm]<br>open override fun [onStartVpn](on-start-vpn.md)(tunfd: [ParcelFileDescriptor](https://developer.android.com/reference/kotlin/android/os/ParcelFileDescriptor.html)): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Called before the VPN is started |
| [onStopVpn](on-stop-vpn.md) | [androidJvm]<br>open override fun [onStopVpn](on-stop-vpn.md)(reason: [VpnStateMonitor.VpnStopReason](../../../com.duckduckgo.mobile.android.vpn.state/-vpn-state-monitor/-vpn-stop-reason/index.md)): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Called before the VPN is stopped |
