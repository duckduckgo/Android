//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[AmpLinks](index.md)/[extractCanonicalFromAmpLink](extract-canonical-from-amp-link.md)

# extractCanonicalFromAmpLink

[jvm]\
abstract fun [extractCanonicalFromAmpLink](extract-canonical-from-amp-link.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [AmpLinkType](../-amp-link-type/index.md)?

This method takes a [url](extract-canonical-from-amp-link.md) and returns a [AmpLinkType](../-amp-link-type/index.md) depending on the detected AMP link.

#### Return

the [AmpLinkType](../-amp-link-type/index.md) or `null` if the [url](extract-canonical-from-amp-link.md) is not an AMP link.
