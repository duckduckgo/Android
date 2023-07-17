//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[insertFolderBranch](insert-folder-branch.md)

# insertFolderBranch

[androidJvm]\
abstract fun [insertFolderBranch](insert-folder-branch.md)(branchToInsert: [FolderBranch](../../com.duckduckgo.savedsites.api.models/-folder-branch/index.md))

Inserts all [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) and [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) in a folder. Used when Undoing [deleteFolderBranch](delete-folder-branch.md)

#### Return

[FolderBranch](../../com.duckduckgo.savedsites.api.models/-folder-branch/index.md) inserted

#### Parameters

androidJvm

| | |
|---|---|
| branchToInsert | the [FolderBranch](../../com.duckduckgo.savedsites.api.models/-folder-branch/index.md) previously deleted |
