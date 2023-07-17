//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[VpnNetworkCallback](index.md)/[isDomainBlocked](is-domain-blocked.md)

# isDomainBlocked

[jvm]\
abstract fun [isDomainBlocked](is-domain-blocked.md)(domainRR: [DomainRR](../-domain-r-r/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

Called by the VPN network to know if a domain is blocked or not. This is used to perform DNS-base tracker blocking

#### Parameters

jvm

| | |
|---|---|
| domainRR | is the domain record |
