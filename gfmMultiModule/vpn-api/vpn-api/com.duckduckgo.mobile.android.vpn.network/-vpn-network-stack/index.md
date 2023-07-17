//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../index.md)/[VpnNetworkStack](index.md)

# VpnNetworkStack

interface [VpnNetworkStack](index.md)

#### Inheritors

| |
|---|
| [EmptyVpnNetworkStack](-empty-vpn-network-stack/index.md) |

## Types

| Name | Summary |
|---|---|
| [EmptyVpnNetworkStack](-empty-vpn-network-stack/index.md) | [androidJvm]<br>object [EmptyVpnNetworkStack](-empty-vpn-network-stack/index.md) : [VpnNetworkStack](index.md) |
| [VpnTunnelConfig](-vpn-tunnel-config/index.md) | [androidJvm]<br>data class [VpnTunnelConfig](-vpn-tunnel-config/index.md)(val mtu: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val addresses: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, val dns: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html)&gt;, val routes: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, val appExclusionList: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)<br>Additional configuration data to be set to the VPN tunnel |

## Properties

| Name | Summary |
|---|---|
| [name](name.md) | [androidJvm]<br>abstract val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Name of the networking layer |

## Functions

| Name | Summary |
|---|---|
| [onCreateVpn](on-create-vpn.md) | [androidJvm]<br>abstract fun [onCreateVpn](on-create-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt; |
| [onDestroyVpn](on-destroy-vpn.md) | [androidJvm]<br>abstract fun [onDestroyVpn](on-destroy-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Clean when the networking layer is destroyed. You can use this method to clean up resources |
| [onPrepareVpn](on-prepare-vpn.md) | [androidJvm]<br>abstract suspend fun [onPrepareVpn](on-prepare-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[VpnNetworkStack.VpnTunnelConfig](-vpn-tunnel-config/index.md)&gt;<br>Called before the vpn tunnel is created and before the vpn is started. |
| [onStartVpn](on-start-vpn.md) | [androidJvm]<br>abstract fun [onStartVpn](on-start-vpn.md)(tunfd: [ParcelFileDescriptor](https://developer.android.com/reference/kotlin/android/os/ParcelFileDescriptor.html)): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Called before the VPN is started |
| [onStopVpn](on-stop-vpn.md) | [androidJvm]<br>abstract fun [onStopVpn](on-stop-vpn.md)(reason: [VpnStateMonitor.VpnStopReason](../../com.duckduckgo.mobile.android.vpn.state/-vpn-state-monitor/-vpn-stop-reason/index.md)): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;<br>Called before the VPN is stopped |
