//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[VpnNetworkCallback](index.md)/[isAddressBlocked](is-address-blocked.md)

# isAddressBlocked

[jvm]\
abstract fun [isAddressBlocked](is-address-blocked.md)(addressRR: [AddressRR](../-address-r-r/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

Called by the VPN network to know if a particular IP address is blocked or not. This can be combined with the [onDnsResolved](on-dns-resolved.md) callback, to get the hostname of the [addressRR](is-address-blocked.md) and then decide whether that hostname should be blocked or not

#### Parameters

jvm

| | |
|---|---|
| addressRR | is the address record |
