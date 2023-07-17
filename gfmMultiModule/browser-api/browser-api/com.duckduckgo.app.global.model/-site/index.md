//[browser-api](../../../index.md)/[com.duckduckgo.app.global.model](../index.md)/[Site](index.md)

# Site

[androidJvm]\
interface [Site](index.md)

## Properties

| Name | Summary |
|---|---|
| [allTrackersBlocked](all-trackers-blocked.md) | [androidJvm]<br>abstract val [allTrackersBlocked](all-trackers-blocked.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [baseHost](../base-host.md) | [androidJvm]<br>val [Site](index.md).[baseHost](../base-host.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [certificate](certificate.md) | [androidJvm]<br>abstract var [certificate](certificate.md): [SslCertificate](https://developer.android.com/reference/kotlin/android/net/http/SslCertificate.html)? |
| [consentCosmeticHide](consent-cosmetic-hide.md) | [androidJvm]<br>abstract var [consentCosmeticHide](consent-cosmetic-hide.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)? |
| [consentManaged](consent-managed.md) | [androidJvm]<br>abstract var [consentManaged](consent-managed.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [consentOptOutFailed](consent-opt-out-failed.md) | [androidJvm]<br>abstract var [consentOptOutFailed](consent-opt-out-failed.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [consentSelfTestFailed](consent-self-test-failed.md) | [androidJvm]<br>abstract var [consentSelfTestFailed](consent-self-test-failed.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [domain](../domain.md) | [androidJvm]<br>val [Site](index.md).[domain](../domain.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [entity](entity.md) | [androidJvm]<br>abstract val [entity](entity.md): [Entity](../../com.duckduckgo.app.trackerdetection.model/-entity/index.md)? |
| [hasHttpResources](has-http-resources.md) | [androidJvm]<br>abstract var [hasHttpResources](has-http-resources.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [https](https.md) | [androidJvm]<br>abstract val [https](https.md): [HttpsStatus](../../com.duckduckgo.app.privacy.model/-https-status/index.md) |
| [majorNetworkCount](major-network-count.md) | [androidJvm]<br>abstract val [majorNetworkCount](major-network-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [otherDomainsLoadedCount](other-domains-loaded-count.md) | [androidJvm]<br>abstract val [otherDomainsLoadedCount](other-domains-loaded-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [specialDomainsLoadedCount](special-domains-loaded-count.md) | [androidJvm]<br>abstract val [specialDomainsLoadedCount](special-domains-loaded-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [surrogates](surrogates.md) | [androidJvm]<br>abstract val [surrogates](surrogates.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[SurrogateResponse](../../com.duckduckgo.app.surrogates/-surrogate-response/index.md)&gt; |
| [title](title.md) | [androidJvm]<br>abstract var [title](title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [trackerCount](tracker-count.md) | [androidJvm]<br>abstract val [trackerCount](tracker-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [trackingEvents](tracking-events.md) | [androidJvm]<br>abstract val [trackingEvents](tracking-events.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[TrackingEvent](../../com.duckduckgo.app.trackerdetection.model/-tracking-event/index.md)&gt; |
| [upgradedHttps](upgraded-https.md) | [androidJvm]<br>abstract var [upgradedHttps](upgraded-https.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [uri](uri.md) | [androidJvm]<br>abstract val [uri](uri.md): [Uri](https://developer.android.com/reference/kotlin/android/net/Uri.html)? |
| [url](url.md) | [androidJvm]<br>abstract var [url](url.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [urlParametersRemoved](url-parameters-removed.md) | [androidJvm]<br>abstract var [urlParametersRemoved](url-parameters-removed.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [userAllowList](user-allow-list.md) | [androidJvm]<br>abstract var [userAllowList](user-allow-list.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |

## Functions

| Name | Summary |
|---|---|
| [domainMatchesUrl](../domain-matches-url.md) | [androidJvm]<br>fun [Site](index.md).[domainMatchesUrl](../domain-matches-url.md)(matchingUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [orderedTrackerBlockedEntities](../ordered-tracker-blocked-entities.md) | [androidJvm]<br>fun [Site](index.md).[orderedTrackerBlockedEntities](../ordered-tracker-blocked-entities.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Entity](../../com.duckduckgo.app.trackerdetection.model/-entity/index.md)&gt; |
| [privacyProtection](privacy-protection.md) | [androidJvm]<br>abstract fun [privacyProtection](privacy-protection.md)(): [PrivacyShield](../-privacy-shield/index.md) |
| [surrogateDetected](surrogate-detected.md) | [androidJvm]<br>abstract fun [surrogateDetected](surrogate-detected.md)(surrogate: [SurrogateResponse](../../com.duckduckgo.app.surrogates/-surrogate-response/index.md)) |
| [trackerDetected](tracker-detected.md) | [androidJvm]<br>abstract fun [trackerDetected](tracker-detected.md)(event: [TrackingEvent](../../com.duckduckgo.app.trackerdetection.model/-tracking-event/index.md)) |
| [updatePrivacyData](update-privacy-data.md) | [androidJvm]<br>abstract fun [updatePrivacyData](update-privacy-data.md)(sitePrivacyData: [SitePrivacyData](../-site-privacy-data/index.md)) |
