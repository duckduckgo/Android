//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[replaceBookmark](replace-bookmark.md)

# replaceBookmark

[androidJvm]\
abstract fun [replaceBookmark](replace-bookmark.md)(bookmark: [SavedSite.Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md), localId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Replaces an existing [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) Used when syncing data from the backend There are scenarios when a duplicate remote bookmark has to be replace the local one

#### Parameters

androidJvm

| | |
|---|---|
| bookmark | the bookmark to replace locally |
| localId | the id of the local bookmark to be replaced |
