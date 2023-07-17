//[browser-api](../../index.md)/[com.duckduckgo.app.global.model](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [PrivacyShield](-privacy-shield/index.md) | [androidJvm]<br>enum [PrivacyShield](-privacy-shield/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[PrivacyShield](-privacy-shield/index.md)&gt; |
| [Site](-site/index.md) | [androidJvm]<br>interface [Site](-site/index.md) |
| [SiteFactory](-site-factory/index.md) | [androidJvm]<br>interface [SiteFactory](-site-factory/index.md) |
| [SitePrivacyData](-site-privacy-data/index.md) | [androidJvm]<br>data class [SitePrivacyData](-site-privacy-data/index.md)(val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val entity: [Entity](../com.duckduckgo.app.trackerdetection.model/-entity/index.md)?, val prevalence: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/index.html)?) |

## Properties

| Name | Summary |
|---|---|
| [baseHost](base-host.md) | [androidJvm]<br>val [Site](-site/index.md).[baseHost](base-host.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [domain](domain.md) | [androidJvm]<br>val [Site](-site/index.md).[domain](domain.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [domainMatchesUrl](domain-matches-url.md) | [androidJvm]<br>fun [Site](-site/index.md).[domainMatchesUrl](domain-matches-url.md)(matchingUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [orderedTrackerBlockedEntities](ordered-tracker-blocked-entities.md) | [androidJvm]<br>fun [Site](-site/index.md).[orderedTrackerBlockedEntities](ordered-tracker-blocked-entities.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Entity](../com.duckduckgo.app.trackerdetection.model/-entity/index.md)&gt; |
