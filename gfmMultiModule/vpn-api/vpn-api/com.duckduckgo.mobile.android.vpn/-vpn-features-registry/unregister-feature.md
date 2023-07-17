//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn](../index.md)/[VpnFeaturesRegistry](index.md)/[unregisterFeature](unregister-feature.md)

# unregisterFeature

[androidJvm]\
abstract suspend fun [unregisterFeature](unregister-feature.md)(feature: [VpnFeature](../-vpn-feature/index.md))

Call this method to unregister a feature that requires VPN access. If the VPN will be disabled if and only if this is the last registered feature.
