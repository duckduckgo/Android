//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[Gpc](index.md)/[getHeaders](get-headers.md)

# getHeaders

[jvm]\
abstract fun [getHeaders](get-headers.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;

This method returns a [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html) with the GPC headers IF the url passed allows for them to be added.

#### Return

a [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html) with the GPC headers or an empty [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html) if the above conditions are not met
