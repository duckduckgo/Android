//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[VpnNetwork](index.md)/[run](run.md)

# run

[jvm]\
abstract fun [run](run.md)(contextId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), tunfd: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))

Runs the VPN networking layer. It will start consuming/producing packets from/to the TUN interface

#### Parameters

jvm

| | |
|---|---|
| contextId | is the network handler identifier returned in [create](create.md) |
| tunfd | is the TUN interface file descriptor |
