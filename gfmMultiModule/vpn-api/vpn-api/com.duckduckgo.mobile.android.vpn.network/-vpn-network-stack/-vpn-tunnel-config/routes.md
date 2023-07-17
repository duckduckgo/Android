//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../../index.md)/[VpnNetworkStack](../index.md)/[VpnTunnelConfig](index.md)/[routes](routes.md)

# routes

[androidJvm]\
val [routes](routes.md): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;

#### Parameters

androidJvm

| | |
|---|---|
| routes | the routes (if any) you wish to add to the VPN service. The Map<String, Int> contains the String IP address and the Int mask. If no routes are returned, the VPN will apply its own defaults. |
