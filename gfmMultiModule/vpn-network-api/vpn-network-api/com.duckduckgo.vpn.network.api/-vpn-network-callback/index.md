//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[VpnNetworkCallback](index.md)

# VpnNetworkCallback

[jvm]\
interface [VpnNetworkCallback](index.md)

## Functions

| Name | Summary |
|---|---|
| [isAddressBlocked](is-address-blocked.md) | [jvm]<br>abstract fun [isAddressBlocked](is-address-blocked.md)(addressRR: [AddressRR](../-address-r-r/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Called by the VPN network to know if a particular IP address is blocked or not. This can be combined with the [onDnsResolved](on-dns-resolved.md) callback, to get the hostname of the [addressRR](is-address-blocked.md) and then decide whether that hostname should be blocked or not |
| [isDomainBlocked](is-domain-blocked.md) | [jvm]<br>abstract fun [isDomainBlocked](is-domain-blocked.md)(domainRR: [DomainRR](../-domain-r-r/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Called by the VPN network to know if a domain is blocked or not. This is used to perform DNS-base tracker blocking |
| [onDnsResolved](on-dns-resolved.md) | [jvm]<br>abstract fun [onDnsResolved](on-dns-resolved.md)(dnsRR: [DnsRR](../-dns-r-r/index.md))<br>Called when the VPN network detects a DNS resource is resolved |
| [onError](on-error.md) | [jvm]<br>abstract fun [onError](on-error.md)(errorCode: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), message: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Called when the VPN network stops upon error. |
| [onExit](on-exit.md) | [jvm]<br>abstract fun [onExit](on-exit.md)(reason: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Called when the VPN network exists unexpectedly. |
