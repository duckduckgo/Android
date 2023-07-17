//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.network](../../index.md)/[VpnNetworkStack](../index.md)/[VpnTunnelConfig](index.md)

# VpnTunnelConfig

data class [VpnTunnelConfig](index.md)(val mtu: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val addresses: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, val dns: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html)&gt;, val routes: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, val appExclusionList: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)

Additional configuration data to be set to the VPN tunnel

#### Parameters

androidJvm

| | |
|---|---|
| mtu | the MTU size you wish the VPN service to set |
| addresses | the address you wish to set to the VPN service. They key contains the InetAddress of the address and value should be the mask width. |
| dns | the additional dns servers you wish to add to the VPN service |
| routes | the routes (if any) you wish to add to the VPN service. The Map<String, Int> contains the String IP address and the Int mask. If no routes are returned, the VPN will apply its own defaults. |
| appExclusionList | the list of apps you wish to exclude from the VPN tunnel |

## Constructors

| | |
|---|---|
| [VpnTunnelConfig](-vpn-tunnel-config.md) | [androidJvm]<br>constructor(mtu: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), addresses: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, dns: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html)&gt;, routes: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, appExclusionList: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [addresses](addresses.md) | [androidJvm]<br>val [addresses](addresses.md): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt; |
| [appExclusionList](app-exclusion-list.md) | [androidJvm]<br>val [appExclusionList](app-exclusion-list.md): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |
| [dns](dns.md) | [androidJvm]<br>val [dns](dns.md): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/index.html)&lt;[InetAddress](https://developer.android.com/reference/kotlin/java/net/InetAddress.html)&gt; |
| [mtu](mtu.md) | [androidJvm]<br>val [mtu](mtu.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [routes](routes.md) | [androidJvm]<br>val [routes](routes.md): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt; |
