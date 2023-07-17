//[browser-api](../../../index.md)/[com.duckduckgo.app.global.model](../index.md)/[SiteFactory](index.md)

# SiteFactory

[androidJvm]\
interface [SiteFactory](index.md)

## Functions

| Name | Summary |
|---|---|
| [buildSite](build-site.md) | [androidJvm]<br>@[AnyThread](https://developer.android.com/reference/kotlin/androidx/annotation/AnyThread.html)<br>abstract fun [buildSite](build-site.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, httpUpgraded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false): [Site](../-site/index.md) |
| [loadFullSiteDetails](load-full-site-details.md) | [androidJvm]<br>@[WorkerThread](https://developer.android.com/reference/kotlin/androidx/annotation/WorkerThread.html)<br>abstract fun [loadFullSiteDetails](load-full-site-details.md)(site: [Site](../-site/index.md))<br>Updates the given Site with the full details |
