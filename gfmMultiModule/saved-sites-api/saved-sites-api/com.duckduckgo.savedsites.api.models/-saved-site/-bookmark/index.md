//[saved-sites-api](../../../../index.md)/[com.duckduckgo.savedsites.api.models](../../index.md)/[SavedSite](../index.md)/[Bookmark](index.md)

# Bookmark

[androidJvm]\
data class [Bookmark](index.md)(val id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val parentId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = SavedSitesNames.BOOKMARKS_ROOT, val lastModified: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val deleted: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) : [SavedSite](../index.md)

## Constructors

| | |
|---|---|
| [Bookmark](-bookmark.md) | [androidJvm]<br>constructor(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), parentId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = SavedSitesNames.BOOKMARKS_ROOT, lastModified: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, deleted: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [deleted](deleted.md) | [androidJvm]<br>open override val [deleted](deleted.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [id](id.md) | [androidJvm]<br>open override val [id](id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [lastModified](last-modified.md) | [androidJvm]<br>open override val [lastModified](last-modified.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [parentId](parent-id.md) | [androidJvm]<br>val [parentId](parent-id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [title](title.md) | [androidJvm]<br>open override val [title](title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [url](url.md) | [androidJvm]<br>open override val [url](url.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
