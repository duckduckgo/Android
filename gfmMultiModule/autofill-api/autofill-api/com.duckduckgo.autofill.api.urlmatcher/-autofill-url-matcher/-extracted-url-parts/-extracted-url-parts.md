//[autofill-api](../../../../index.md)/[com.duckduckgo.autofill.api.urlmatcher](../../index.md)/[AutofillUrlMatcher](../index.md)/[ExtractedUrlParts](index.md)/[ExtractedUrlParts](-extracted-url-parts.md)

# ExtractedUrlParts

[androidJvm]\
constructor(eTldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, userFacingETldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, subdomain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, port: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null)

#### Parameters

androidJvm

| | |
|---|---|
| eTldPlus1 | the eTldPlus1 of the URL, an important component when considering whether two URLs are matching for autofill or not. IDNA-encoded if domain contains non-ascii. |
| userFacingETldPlus1 | the user-facing eTldPlus1. eTldPlus1 might be IDNA-encoded whereas this will be Unicode-encoded, and therefore might contain non-ascii characters. |
| subdomain | the subdomain of the URL or null if there was no subdomain. |
| port | the port of the URL or null if there was no explicit port. |
