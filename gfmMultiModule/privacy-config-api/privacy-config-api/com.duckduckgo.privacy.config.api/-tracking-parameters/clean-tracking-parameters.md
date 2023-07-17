//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[TrackingParameters](index.md)/[cleanTrackingParameters](clean-tracking-parameters.md)

# cleanTrackingParameters

[jvm]\
abstract fun [cleanTrackingParameters](clean-tracking-parameters.md)(initiatingUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?

This method takes an optional [initiatingUrl](clean-tracking-parameters.md) and a [url](clean-tracking-parameters.md) and returns a [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) containing the cleaned URL with tracking parameters removed.

#### Return

the URL [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) or `null` if the [url](clean-tracking-parameters.md) does not contain tracking parameters.
