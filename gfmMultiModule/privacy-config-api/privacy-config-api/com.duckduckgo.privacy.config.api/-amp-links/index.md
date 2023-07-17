//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[AmpLinks](index.md)

# AmpLinks

[jvm]\
interface [AmpLinks](index.md)

Public interface for the AMP Links feature

## Properties

| Name | Summary |
|---|---|
| [lastAmpLinkInfo](last-amp-link-info.md) | [jvm]<br>abstract var [lastAmpLinkInfo](last-amp-link-info.md): [AmpLinkInfo](../-amp-link-info/index.md)?<br>The last AMP link and its destination URL. |

## Functions

| Name | Summary |
|---|---|
| [extractCanonicalFromAmpLink](extract-canonical-from-amp-link.md) | [jvm]<br>abstract fun [extractCanonicalFromAmpLink](extract-canonical-from-amp-link.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [AmpLinkType](../-amp-link-type/index.md)?<br>This method takes a [url](extract-canonical-from-amp-link.md) and returns a [AmpLinkType](../-amp-link-type/index.md) depending on the detected AMP link. |
| [isAnException](is-an-exception.md) | [jvm]<br>abstract fun [isAnException](is-an-exception.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [url](is-an-exception.md) and returns `true` or `false` depending if the [url](is-an-exception.md) is in the AMP links exceptions list |
