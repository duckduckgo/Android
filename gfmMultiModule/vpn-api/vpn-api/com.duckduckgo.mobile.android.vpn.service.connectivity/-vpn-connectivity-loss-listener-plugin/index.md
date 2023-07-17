//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service.connectivity](../index.md)/[VpnConnectivityLossListenerPlugin](index.md)

# VpnConnectivityLossListenerPlugin

[androidJvm]\
interface [VpnConnectivityLossListenerPlugin](index.md)

## Functions

| Name | Summary |
|---|---|
| [onVpnConnected](on-vpn-connected.md) | [androidJvm]<br>open fun [onVpnConnected](on-vpn-connected.md)(coroutineScope: CoroutineScope)<br>This method will be called whenever both the VPN and Device have connectivity. Be aware that this would be called periodically, regardless if the VPN connectivity loss was detected prior or not. |
| [onVpnConnectivityLoss](on-vpn-connectivity-loss.md) | [androidJvm]<br>open fun [onVpnConnectivityLoss](on-vpn-connectivity-loss.md)(coroutineScope: CoroutineScope)<br>This method will be called whenever we detect a VPN connectivity loss while the device actually has connectivity. |
