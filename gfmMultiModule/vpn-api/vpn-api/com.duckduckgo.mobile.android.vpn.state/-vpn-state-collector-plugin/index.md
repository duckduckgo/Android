//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.state](../index.md)/[VpnStateCollectorPlugin](index.md)

# VpnStateCollectorPlugin

[androidJvm]\
interface [VpnStateCollectorPlugin](index.md)

Implement this interface and return the multibinding to return VPN-relevant state information

The VPN will call all the [collectVpnRelatedState](collect-vpn-related-state.md) methods when it wants to send information about the VPN state. This is generally done when an event happens that requires an AppTP bugreport to be sent, eg. user unprotects an app or reports breakage.

## Properties

| Name | Summary |
|---|---|
| [collectorName](collector-name.md) | [androidJvm]<br>abstract val [collectorName](collector-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [collectVpnRelatedState](collect-vpn-related-state.md) | [androidJvm]<br>abstract suspend fun [collectVpnRelatedState](collect-vpn-related-state.md)(appPackageId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null): [JSONObject](https://developer.android.com/reference/kotlin/org/json/JSONObject.html) |
