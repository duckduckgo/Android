//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsManager](index.md)

# SitePermissionsManager

[androidJvm]\
interface [SitePermissionsManager](index.md)

Public interface for managing site permissions data

## Functions

| Name | Summary |
|---|---|
| [clearAllButFireproof](clear-all-but-fireproof.md) | [androidJvm]<br>abstract suspend fun [clearAllButFireproof](clear-all-but-fireproof.md)(fireproofDomains: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;)<br>Deletes all site permissions but the ones that are fireproof |
| [getSitePermissionsAllowedToAsk](get-site-permissions-allowed-to-ask.md) | [androidJvm]<br>abstract suspend fun [getSitePermissionsAllowedToAsk](get-site-permissions-allowed-to-ask.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resources: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;<br>Returns an array of permissions that we support and user didn't deny for given website |
| [getSitePermissionsGranted](get-site-permissions-granted.md) | [androidJvm]<br>abstract suspend fun [getSitePermissionsGranted](get-site-permissions-granted.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resources: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;<br>Returns an array of already granted site permissions. That could be:     - Permission is always allowed for this website     - Permission has been granted within 24h for same site and same tab |
