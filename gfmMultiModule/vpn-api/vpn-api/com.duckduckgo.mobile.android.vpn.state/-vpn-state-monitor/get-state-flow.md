//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.state](../index.md)/[VpnStateMonitor](index.md)/[getStateFlow](get-state-flow.md)

# getStateFlow

[androidJvm]\
abstract fun [getStateFlow](get-state-flow.md)(vpnFeature: [VpnFeature](../../com.duckduckgo.mobile.android.vpn/-vpn-feature/index.md)): Flow&lt;[VpnStateMonitor.VpnState](-vpn-state/index.md)&gt;

Returns a flow of VPN changes for the given [vpnFeature](get-state-flow.md) It follows the following truth table:

- 
   when the VPN is disabled the flow will emit a VpnState.DISABLED
- 
   else it will return the state of the feature
