//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[TrackingParameters](index.md)

# TrackingParameters

[jvm]\
interface [TrackingParameters](index.md)

Public interface for the Tracking Parameters feature

## Properties

| Name | Summary |
|---|---|
| [lastCleanedUrl](last-cleaned-url.md) | [jvm]<br>abstract var [lastCleanedUrl](last-cleaned-url.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>The last tracking parameter cleaned URL. |

## Functions

| Name | Summary |
|---|---|
| [cleanTrackingParameters](clean-tracking-parameters.md) | [jvm]<br>abstract fun [cleanTrackingParameters](clean-tracking-parameters.md)(initiatingUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>This method takes an optional [initiatingUrl](clean-tracking-parameters.md) and a [url](clean-tracking-parameters.md) and returns a [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) containing the cleaned URL with tracking parameters removed. |
