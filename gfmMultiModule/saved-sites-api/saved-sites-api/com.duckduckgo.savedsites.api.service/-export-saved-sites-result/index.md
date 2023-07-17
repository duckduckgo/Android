//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api.service](../index.md)/[ExportSavedSitesResult](index.md)

# ExportSavedSitesResult

sealed class [ExportSavedSitesResult](index.md)

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [Error](-error/index.md) |
| [NoSavedSitesExported](-no-saved-sites-exported/index.md) |

## Types

| Name | Summary |
|---|---|
| [Error](-error/index.md) | [androidJvm]<br>data class [Error](-error/index.md)(val exception: [Exception](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-exception/index.html)) : [ExportSavedSitesResult](index.md) |
| [NoSavedSitesExported](-no-saved-sites-exported/index.md) | [androidJvm]<br>object [NoSavedSitesExported](-no-saved-sites-exported/index.md) : [ExportSavedSitesResult](index.md) |
| [Success](-success/index.md) | [androidJvm]<br>object [Success](-success/index.md) : [ExportSavedSitesResult](index.md) |
