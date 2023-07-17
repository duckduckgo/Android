//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn](../index.md)/[VpnFeaturesRegistry](index.md)

# VpnFeaturesRegistry

[androidJvm]\
interface [VpnFeaturesRegistry](index.md)

Use this class to register features that required VPN access.

Registering a feature will cause the VPN to be enabled. Unregistering a feature will cause the VPN to be disabled IF no other feature is registered.

## Functions

| Name | Summary |
|---|---|
| [getRegisteredFeatures](get-registered-features.md) | [androidJvm]<br>abstract suspend fun [getRegisteredFeatures](get-registered-features.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[VpnFeature](../-vpn-feature/index.md)&gt; |
| [isAnyFeatureRegistered](is-any-feature-registered.md) | [androidJvm]<br>abstract suspend fun [isAnyFeatureRegistered](is-any-feature-registered.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isAnyFeatureRunning](is-any-feature-running.md) | [androidJvm]<br>abstract suspend fun [isAnyFeatureRunning](is-any-feature-running.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isFeatureRegistered](is-feature-registered.md) | [androidJvm]<br>abstract suspend fun [isFeatureRegistered](is-feature-registered.md)(feature: [VpnFeature](../-vpn-feature/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isFeatureRunning](is-feature-running.md) | [androidJvm]<br>abstract suspend fun [isFeatureRunning](is-feature-running.md)(feature: [VpnFeature](../-vpn-feature/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [refreshFeature](refresh-feature.md) | [androidJvm]<br>abstract suspend fun [refreshFeature](refresh-feature.md)(feature: [VpnFeature](../-vpn-feature/index.md))<br>Refreshing the feature will cause the VPN to be stopped/restarted if it is enabled and the feature is already registered. |
| [registerFeature](register-feature.md) | [androidJvm]<br>abstract suspend fun [registerFeature](register-feature.md)(feature: [VpnFeature](../-vpn-feature/index.md))<br>Call this method to register a feature that requires VPN access. If the VPN is not enabled, it will be enabled. |
| [unregisterFeature](unregister-feature.md) | [androidJvm]<br>abstract suspend fun [unregisterFeature](unregister-feature.md)(feature: [VpnFeature](../-vpn-feature/index.md))<br>Call this method to unregister a feature that requires VPN access. If the VPN will be disabled if and only if this is the last registered feature. |
