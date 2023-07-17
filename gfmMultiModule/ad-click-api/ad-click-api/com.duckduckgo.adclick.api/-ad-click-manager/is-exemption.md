//[ad-click-api](../../../index.md)/[com.duckduckgo.adclick.api](../index.md)/[AdClickManager](index.md)/[isExemption](is-exemption.md)

# isExemption

[jvm]\
abstract fun [isExemption](is-exemption.md)(documentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

Detects if there is an existing exemption based on the document url and the url requested. It takes as parameters: mandatory [documentUrl](is-exemption.md) - The initially requested url, potentially leading to the advertiser page. mandatory [url](is-exemption.md) - The requested url, potentially a tracker used for ad attribution.

#### Return

`true` if there is an existing exemption for this combination of [documentUrl](is-exemption.md) and [url](is-exemption.md), false otherwise.
