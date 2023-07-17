//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[Gpc](index.md)/[canUrlAddHeaders](can-url-add-headers.md)

# canUrlAddHeaders

[jvm]\
abstract fun [canUrlAddHeaders](can-url-add-headers.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), existingHeaders: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This method takes a [url](can-url-add-headers.md) and a map with its [existingHeaders](can-url-add-headers.md) and it then returns `true` if the given [url](can-url-add-headers.md) can add the GPC headers

#### Return

a `true` if the given [url](can-url-add-headers.md) and [existingHeaders](can-url-add-headers.md) permit the GPC headers to be added
