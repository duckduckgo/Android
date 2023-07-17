//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service.connectivity](../index.md)/[VpnConnectivityLossListenerPlugin](index.md)/[onVpnConnected](on-vpn-connected.md)

# onVpnConnected

[androidJvm]\
open fun [onVpnConnected](on-vpn-connected.md)(coroutineScope: CoroutineScope)

This method will be called whenever both the VPN and Device have connectivity. Be aware that this would be called periodically, regardless if the VPN connectivity loss was detected prior or not.
