//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[getAllFolderContentSync](get-all-folder-content-sync.md)

# getAllFolderContentSync

[androidJvm]\
abstract fun [getAllFolderContentSync](get-all-folder-content-sync.md)(folderId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Pair](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[SavedSite.Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md)&gt;, [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md)&gt;&gt;

Returns all [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) and [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) inside a folder, also deleted objects

#### Return

[Pair](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html) of [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) and [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) inside a folder

#### Parameters

androidJvm

| | |
|---|---|
| folderId | the id of the folder. |
