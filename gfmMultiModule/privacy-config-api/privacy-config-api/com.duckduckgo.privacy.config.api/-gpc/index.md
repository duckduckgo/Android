//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[Gpc](index.md)

# Gpc

[jvm]\
interface [Gpc](index.md)

Public interface for the Gpc feature

## Functions

| Name | Summary |
|---|---|
| [canUrlAddHeaders](can-url-add-headers.md) | [jvm]<br>abstract fun [canUrlAddHeaders](can-url-add-headers.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), existingHeaders: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [url](can-url-add-headers.md) and a map with its [existingHeaders](can-url-add-headers.md) and it then returns `true` if the given [url](can-url-add-headers.md) can add the GPC headers |
| [disableGpc](disable-gpc.md) | [jvm]<br>abstract fun [disableGpc](disable-gpc.md)()<br>This method is used to disable GPC. Note: The remote configuration will take precedence over this value. |
| [enableGpc](enable-gpc.md) | [jvm]<br>abstract fun [enableGpc](enable-gpc.md)()<br>This method is used to enable GPC. Note: The remote configuration will take precedence over this value. |
| [getHeaders](get-headers.md) | [jvm]<br>abstract fun [getHeaders](get-headers.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;<br>This method returns a [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html) with the GPC headers IF the url passed allows for them to be added. |
| [isEnabled](is-enabled.md) | [jvm]<br>abstract fun [isEnabled](is-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This takes into account two different inputs. |
