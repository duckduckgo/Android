//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[VpnNetwork](index.md)

# VpnNetwork

[jvm]\
interface [VpnNetwork](index.md)

## Functions

| Name | Summary |
|---|---|
| [addCallback](add-callback.md) | [jvm]<br>abstract fun [addCallback](add-callback.md)(callback: [VpnNetworkCallback](../-vpn-network-callback/index.md)?)<br>Register a [VpnNetworkCallback](../-vpn-network-callback/index.md) to get notified of some network events |
| [create](create.md) | [jvm]<br>abstract fun [create](create.md)(): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)<br>Creates the Networking layer |
| [destroy](destroy.md) | [jvm]<br>abstract fun [destroy](destroy.md)(contextId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))<br>Clears the VPN networking layer resource |
| [mtu](mtu.md) | [jvm]<br>abstract fun [mtu](mtu.md)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [run](run.md) | [jvm]<br>abstract fun [run](run.md)(contextId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), tunfd: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Runs the VPN networking layer. It will start consuming/producing packets from/to the TUN interface |
| [start](start.md) | [jvm]<br>abstract fun [start](start.md)(contextId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), logLevel: [VpnNetworkLog](../-vpn-network-log/index.md))<br>Starts the networking layer |
| [stop](stop.md) | [jvm]<br>abstract fun [stop](stop.md)(contextId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))<br>Stops the VPN networking layer |
