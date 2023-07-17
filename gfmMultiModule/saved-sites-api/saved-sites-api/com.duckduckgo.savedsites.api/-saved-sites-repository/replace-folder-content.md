//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[replaceFolderContent](replace-folder-content.md)

# replaceFolderContent

[androidJvm]\
abstract fun [replaceFolderContent](replace-folder-content.md)(folder: [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md), oldId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Replaces an existing [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) Used when syncing data from the backend There are scenarios when a duplicate remote folder has to be replace the local one

#### Parameters

androidJvm

| | |
|---|---|
| folder | the folder that will replace localId |
| localId | the id of the local folder to be replaced |
