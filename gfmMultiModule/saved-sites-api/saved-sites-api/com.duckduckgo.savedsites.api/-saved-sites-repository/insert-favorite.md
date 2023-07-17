//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[insertFavorite](insert-favorite.md)

# insertFavorite

[androidJvm]\
abstract fun [insertFavorite](insert-favorite.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = &quot;&quot;, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), lastModified: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null): [SavedSite.Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md)

Inserts a new [Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md) Used when adding a [Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md) from the Browser Menu

#### Return

[Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md) inserted

#### Parameters

androidJvm

| | |
|---|---|
| url | of the site |
| title | of the [Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md) |
