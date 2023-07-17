//[vpn-network-api](../../../index.md)/[com.duckduckgo.vpn.network.api](../index.md)/[DnsRR](index.md)

# DnsRR

[jvm]\
data class [DnsRR](index.md)(val time: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), val qName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val aName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val resource: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val ttl: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))

DNS record type [time](time.md) is the time the DNS resource was resolved [qName](q-name.md) is the DNS record QName [aName](a-name.md) is the DNS record AName [resource](resource.md) is the resource (IP address) [ttl](ttl.md) is the time to live of the DNS record

## Constructors

| | |
|---|---|
| [DnsRR](-dns-r-r.md) | [jvm]<br>constructor(time: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), qName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), aName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resource: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), ttl: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [aName](a-name.md) | [jvm]<br>val [aName](a-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [qName](q-name.md) | [jvm]<br>val [qName](q-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [resource](resource.md) | [jvm]<br>val [resource](resource.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [time](time.md) | [jvm]<br>val [time](time.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [ttl](ttl.md) | [jvm]<br>val [ttl](ttl.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
