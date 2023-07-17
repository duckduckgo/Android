//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[getBookmarksModifiedSince](get-bookmarks-modified-since.md)

# getBookmarksModifiedSince

[androidJvm]\
abstract fun [getBookmarksModifiedSince](get-bookmarks-modified-since.md)(since: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[SavedSite.Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md)&gt;

Returns the list of [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md) modified after [since](get-bookmarks-modified-since.md)

#### Return

[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html) of [Bookmark](../../com.duckduckgo.savedsites.api.models/-saved-site/-bookmark/index.md)

#### Parameters

androidJvm

| | |
|---|---|
| since | timestamp of modification for filtering |
