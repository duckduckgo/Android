//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[insert](insert.md)

# insert

[androidJvm]\
abstract fun [insert](insert.md)(savedSite: [SavedSite](../../com.duckduckgo.savedsites.api.models/-saved-site/index.md)): [SavedSite](../../com.duckduckgo.savedsites.api.models/-saved-site/index.md)

Inserts a new [SavedSite](../../com.duckduckgo.savedsites.api.models/-saved-site/index.md) Used when undoing the deletion of a [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) or [Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md)

#### Return

[SavedSite](../../com.duckduckgo.savedsites.api.models/-saved-site/index.md) inserted

[androidJvm]\
abstract fun [insert](insert.md)(folder: [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md)): [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md)

Inserts a new [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) Used when adding a [BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) from the Bookmarks screen

#### Return

[BookmarkFolder](../../com.duckduckgo.savedsites.api.models/-bookmark-folder/index.md) inserted

#### Parameters

androidJvm

| | |
|---|---|
| folder | to be added |
