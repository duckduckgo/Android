//[saved-sites-api](../../../index.md)/[com.duckduckgo.savedsites.api.models](../index.md)/[SavedSite](index.md)

# SavedSite

sealed class [SavedSite](index.md) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)

#### Inheritors

| |
|---|
| [Favorite](-favorite/index.md) |
| [Bookmark](-bookmark/index.md) |

## Types

| Name | Summary |
|---|---|
| [Bookmark](-bookmark/index.md) | [androidJvm]<br>data class [Bookmark](-bookmark/index.md)(val id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val parentId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = SavedSitesNames.BOOKMARKS_ROOT, val lastModified: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val deleted: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) : [SavedSite](index.md) |
| [Favorite](-favorite/index.md) | [androidJvm]<br>data class [Favorite](-favorite/index.md)(val id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val lastModified: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val position: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val deleted: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) : [SavedSite](index.md) |

## Properties

| Name | Summary |
|---|---|
| [deleted](deleted.md) | [androidJvm]<br>open val [deleted](deleted.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [id](id.md) | [androidJvm]<br>open val [id](id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [lastModified](last-modified.md) | [androidJvm]<br>open val [lastModified](last-modified.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [title](title.md) | [androidJvm]<br>open val [title](title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [url](url.md) | [androidJvm]<br>open val [url](url.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
