//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsManager](index.md)/[getSitePermissionsAllowedToAsk](get-site-permissions-allowed-to-ask.md)

# getSitePermissionsAllowedToAsk

[androidJvm]\
abstract suspend fun [getSitePermissionsAllowedToAsk](get-site-permissions-allowed-to-ask.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resources: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;

Returns an array of permissions that we support and user didn't deny for given website

#### Parameters

androidJvm

| | |
|---|---|
| url | unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
| resources | array of permissions that have been requested by the website |
