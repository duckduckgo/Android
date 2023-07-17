//[site-permissions-api](../../../index.md)/[com.duckduckgo.site.permissions.api](../index.md)/[SitePermissionsManager](index.md)/[getSitePermissionsGranted](get-site-permissions-granted.md)

# getSitePermissionsGranted

[androidJvm]\
abstract suspend fun [getSitePermissionsGranted](get-site-permissions-granted.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resources: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;

Returns an array of already granted site permissions. That could be:     - Permission is always allowed for this website     - Permission has been granted within 24h for same site and same tab

#### Parameters

androidJvm

| | |
|---|---|
| url | unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
| tabId | id from the tab where this method is called from |
| resources | array of permissions that have been requested by the website |
