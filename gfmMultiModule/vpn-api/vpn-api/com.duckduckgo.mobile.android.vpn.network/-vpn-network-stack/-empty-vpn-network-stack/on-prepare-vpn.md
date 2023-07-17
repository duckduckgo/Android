//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../../index.md)/[VpnNetworkStack](../index.md)/[EmptyVpnNetworkStack](index.md)/[onPrepareVpn](on-prepare-vpn.md)

# onPrepareVpn

[androidJvm]\
open suspend override fun [onPrepareVpn](on-prepare-vpn.md)(): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[VpnNetworkStack.VpnTunnelConfig](../-vpn-tunnel-config/index.md)&gt;

Called before the vpn tunnel is created and before the vpn is started.

#### Return

VpnTunnelConfig that will be used to configures the VPN's tunnel.

The signature of this method is [suspend](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/index.html) to allow for asynchronous operations when creating the [VpnTunnelConfig](../-vpn-tunnel-config/index.md).
