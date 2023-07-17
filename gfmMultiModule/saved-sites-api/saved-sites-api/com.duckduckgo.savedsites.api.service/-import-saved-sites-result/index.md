//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api.service](../index.md)/[ImportSavedSitesResult](index.md)

# ImportSavedSitesResult

sealed class [ImportSavedSitesResult](index.md)

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [Error](-error/index.md) |

## Types

| Name | Summary |
|---|---|
| [Error](-error/index.md) | [androidJvm]<br>data class [Error](-error/index.md)(val exception: [Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-exception/index.html)) : [ImportSavedSitesResult](index.md) |
| [Success](-success/index.md) | [androidJvm]<br>data class [Success](-success/index.md)(val savedSites: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[SavedSite](../../com.duckduckgo.savedsites.api.models/-saved-site/index.md)&gt;) : [ImportSavedSitesResult](index.md) |
