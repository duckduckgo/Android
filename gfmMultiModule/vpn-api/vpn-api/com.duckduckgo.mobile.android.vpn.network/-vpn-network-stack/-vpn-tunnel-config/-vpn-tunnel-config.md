//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../../index.md)/[VpnNetworkStack](../index.md)/[VpnTunnelConfig](index.md)/[VpnTunnelConfig](-vpn-tunnel-config.md)

# VpnTunnelConfig

[androidJvm]\
constructor(mtu: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), addresses: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, dns: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html)&gt;, routes: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, appExclusionList: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)

#### Parameters

androidJvm

| | |
|---|---|
| mtu | the MTU size you wish the VPN service to set |
| addresses | the address you wish to set to the VPN service. They key contains the InetAddress of the address and value should be the mask width. |
| dns | the additional dns servers you wish to add to the VPN service |
| routes | the routes (if any) you wish to add to the VPN service. The Map<String, Int> contains the String IP address and the Int mask. If no routes are returned, the VPN will apply its own defaults. |
| appExclusionList | the list of apps you wish to exclude from the VPN tunnel |
