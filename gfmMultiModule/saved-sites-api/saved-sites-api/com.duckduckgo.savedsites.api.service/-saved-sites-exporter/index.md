//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api.service](../index.md)/[SavedSitesExporter](index.md)

# SavedSitesExporter

[androidJvm]\
interface [SavedSitesExporter](index.md)

Class that takes care of exporting SavedSites This is used to export SavedSites to another Browser

## Functions

| Name | Summary |
|---|---|
| [export](export.md) | [androidJvm]<br>abstract suspend fun [export](export.md)(uri: [Uri](https://developer.android.com/reference/kotlin/android/net/Uri.html)): [ExportSavedSitesResult](../-export-saved-sites-result/index.md)<br>Generates a HTML based file with all SavedSites that the user has in Netscape format. |
