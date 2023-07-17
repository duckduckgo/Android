//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[Drm](index.md)/[getDrmPermissionsForRequest](get-drm-permissions-for-request.md)

# getDrmPermissionsForRequest

[jvm]\
abstract fun [getDrmPermissionsForRequest](get-drm-permissions-for-request.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), resources: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;

This method takes a [url](get-drm-permissions-for-request.md) and an Array<String> returns an `Array<String>` depending on the [url](get-drm-permissions-for-request.md)

#### Return

an `Array<String>` if the given [url](get-drm-permissions-for-request.md) is in the eme list and an empty array otherwise.
