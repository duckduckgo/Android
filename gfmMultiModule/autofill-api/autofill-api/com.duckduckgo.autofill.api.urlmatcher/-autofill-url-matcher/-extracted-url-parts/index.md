//[autofill-api](../../../../index.md)/[com.duckduckgo.autofill.api.urlmatcher](../../index.md)/[AutofillUrlMatcher](../index.md)/[ExtractedUrlParts](index.md)

# ExtractedUrlParts

data class [ExtractedUrlParts](index.md)(val eTldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val userFacingETldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val subdomain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val port: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null)

Data class to hold the extracted parts of a URL.

#### Parameters

androidJvm

| | |
|---|---|
| eTldPlus1 | the eTldPlus1 of the URL, an important component when considering whether two URLs are matching for autofill or not. IDNA-encoded if domain contains non-ascii. |
| userFacingETldPlus1 | the user-facing eTldPlus1. eTldPlus1 might be IDNA-encoded whereas this will be Unicode-encoded, and therefore might contain non-ascii characters. |
| subdomain | the subdomain of the URL or null if there was no subdomain. |
| port | the port of the URL or null if there was no explicit port. |

## Constructors

| | |
|---|---|
| [ExtractedUrlParts](-extracted-url-parts.md) | [androidJvm]<br>constructor(eTldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, userFacingETldPlus1: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, subdomain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, port: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [eTldPlus1](e-tld-plus1.md) | [androidJvm]<br>val [eTldPlus1](e-tld-plus1.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [port](port.md) | [androidJvm]<br>val [port](port.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null |
| [subdomain](subdomain.md) | [androidJvm]<br>val [subdomain](subdomain.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [userFacingETldPlus1](user-facing-e-tld-plus1.md) | [androidJvm]<br>val [userFacingETldPlus1](user-facing-e-tld-plus1.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
