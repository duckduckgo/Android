//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../index.md)/[VpnNetworkStack](index.md)/[onStopVpn](on-stop-vpn.md)

# onStopVpn

[androidJvm]\
abstract fun [onStopVpn](on-stop-vpn.md)(reason: [VpnStateMonitor.VpnStopReason](../../com.duckduckgo.mobile.android.vpn.state/-vpn-state-monitor/-vpn-stop-reason/index.md)): [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html)&lt;[Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)&gt;

Called before the VPN is stopped

#### Return

`true` if the VPN is successfully stopped, `false` otherwise
