//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api.service](../index.md)/[SavedSitesImporter](index.md)

# SavedSitesImporter

[androidJvm]\
interface [SavedSitesImporter](index.md)

Class that takes care of importing SavedSites This is used to import SavedSites from another Browser

## Functions

| Name | Summary |
|---|---|
| [import](import.md) | [androidJvm]<br>abstract suspend fun [import](import.md)(uri: [Uri](https://developer.android.com/reference/kotlin/android/net/Uri.html)): [ImportSavedSitesResult](../-import-saved-sites-result/index.md)<br>Reads a HTML based file with all SavedSites that the user has in Netscape format. |
