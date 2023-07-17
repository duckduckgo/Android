//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api](../index.md)/[SavedSitesRepository](index.md)/[replaceFavourite](replace-favourite.md)

# replaceFavourite

[androidJvm]\
abstract fun [replaceFavourite](replace-favourite.md)(favorite: [SavedSite.Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md), localId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Replaces an existing [Favorite](../../com.duckduckgo.savedsites.api.models/-saved-site/-favorite/index.md) Used when syncing data from the backend There are scenarios when a duplicate remote favourite has to be replace the local one

#### Parameters

androidJvm

| | |
|---|---|
| favorite | the favourite to replace locally |
| localId | the local Id to be replaced |
