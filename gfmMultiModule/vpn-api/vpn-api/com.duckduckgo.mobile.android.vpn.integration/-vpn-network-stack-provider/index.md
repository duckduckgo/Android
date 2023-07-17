//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.integration](../index.md)/[VpnNetworkStackProvider](index.md)

# VpnNetworkStackProvider

[androidJvm]\
interface [VpnNetworkStackProvider](index.md)

This class is used to provide the VPN network stack to the app tracking protection module.

Note: This class is exposed in the vpn-api module just temporarily TODO move this class back into the vpn-impl module

## Functions

| Name | Summary |
|---|---|
| [provideNetworkStack](provide-network-stack.md) | [androidJvm]<br>abstract suspend fun [provideNetworkStack](provide-network-stack.md)(): [VpnNetworkStack](../../com.duckduckgo.mobile.android.vpn.network/-vpn-network-stack/index.md)<br>CAll this method to get the VPN network stack to be used in the VPN service. |
