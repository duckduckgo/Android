//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.urlmatcher](../index.md)/[AutofillUrlMatcher](index.md)

# AutofillUrlMatcher

[androidJvm]\
interface [AutofillUrlMatcher](index.md)

This interface is used to clean and match URLs for autofill purposes. It can offer support for:     - When we have a full URL and might have to clean this up before we can save it.     - When we have two URLs and need to determine if they should be treated as matching for autofill purposes.

## Types

| Name | Summary |
|---|---|
| [ExtractedUrlParts](-extracted-url-parts/index.md) | [androidJvm]<br>data class [ExtractedUrlParts](-extracted-url-parts/index.md)(val eTldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val userFacingETldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val subdomain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val port: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null)<br>Data class to hold the extracted parts of a URL. |

## Functions

| Name | Summary |
|---|---|
| [cleanRawUrl](clean-raw-url.md) | [androidJvm]<br>abstract fun [cleanRawUrl](clean-raw-url.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>This method tries to clean up a raw URL. |
| [extractUrlPartsForAutofill](extract-url-parts-for-autofill.md) | [androidJvm]<br>abstract fun [extractUrlPartsForAutofill](extract-url-parts-for-autofill.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [AutofillUrlMatcher.ExtractedUrlParts](-extracted-url-parts/index.md)<br>This method tries to extract the parts of a URL that are relevant for autofill. |
| [matchingForAutofill](matching-for-autofill.md) | [androidJvm]<br>abstract fun [matchingForAutofill](matching-for-autofill.md)(visitedSite: [AutofillUrlMatcher.ExtractedUrlParts](-extracted-url-parts/index.md), savedSite: [AutofillUrlMatcher.ExtractedUrlParts](-extracted-url-parts/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method tries to determine if two URLs are matching for autofill purposes. |
