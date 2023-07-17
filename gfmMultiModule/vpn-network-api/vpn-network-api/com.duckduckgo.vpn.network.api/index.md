//[vpn-network-api](../../index.md)/[com.duckduckgo.vpn.network.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [AddressRR](-address-r-r/index.md) | [jvm]<br>data class [AddressRR](-address-r-r/index.md)(val address: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val uid: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Address record type [address](-address-r-r/address.md) is the [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) IP address [uid](-address-r-r/uid.md) is the UID of the app that's trying to access the address |
| [DnsRR](-dns-r-r/index.md) | [jvm]<br>data class [DnsRR](-dns-r-r/index.md)(val time: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), val qName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val aName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val resource: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val ttl: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>DNS record type [time](-dns-r-r/time.md) is the time the DNS resource was resolved [qName](-dns-r-r/q-name.md) is the DNS record QName [aName](-dns-r-r/a-name.md) is the DNS record AName [resource](-dns-r-r/resource.md) is the resource (IP address) [ttl](-dns-r-r/ttl.md) is the time to live of the DNS record |
| [DomainRR](-domain-r-r/index.md) | [jvm]<br>data class [DomainRR](-domain-r-r/index.md)(val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val uid: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Domain record type [name](-domain-r-r/name.md) is the name of the domain [uid](-domain-r-r/uid.md) is the UID of the app that's trying to access the domain |
| [SniRR](-sni-r-r/index.md) | [jvm]<br>data class [SniRR](-sni-r-r/index.md)(val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val resource: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>SNI record type [name](-sni-r-r/name.md) is the name of the server [resource](-sni-r-r/resource.md) is the address of the server |
| [VpnNetwork](-vpn-network/index.md) | [jvm]<br>interface [VpnNetwork](-vpn-network/index.md) |
| [VpnNetworkCallback](-vpn-network-callback/index.md) | [jvm]<br>interface [VpnNetworkCallback](-vpn-network-callback/index.md) |
| [VpnNetworkLog](-vpn-network-log/index.md) | [jvm]<br>enum [VpnNetworkLog](-vpn-network-log/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnNetworkLog](-vpn-network-log/index.md)&gt; |
