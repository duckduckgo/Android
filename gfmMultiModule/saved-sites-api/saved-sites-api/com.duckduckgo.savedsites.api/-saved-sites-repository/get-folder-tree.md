//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[getFolderTree](get-folder-tree.md)

# getFolderTree

[androidJvm]\
abstract fun [getFolderTree](get-folder-tree.md)(selectedFolderId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), currentFolder: [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md)?): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[BookmarkFolderItem](../../com.duckduckgo.savedsites.api.models/-bookmark-folder-item/index.md)&gt;

Returns complete list of [BookmarkFolderItem](../../com.duckduckgo.savedsites.api.models/-bookmark-folder-item/index.md) inside a folder. This method traverses all folders.

#### Return

[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html) of [BookmarkFolderItem](../../com.duckduckgo.savedsites.api.models/-bookmark-folder-item/index.md) inside a folder

#### Parameters

androidJvm

| | |
|---|---|
| selectedFolderId | the id of the folder. |
| currentFolder | folder currently selected, used to determine the current depth in the tree. |
