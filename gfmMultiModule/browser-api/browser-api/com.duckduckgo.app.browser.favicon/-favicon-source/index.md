//[browser-api](../../../index.md)/[com.duckduckgo.app.browser.favicon](../index.md)/[FaviconSource](index.md)

# FaviconSource

sealed class [FaviconSource](index.md)

#### Inheritors

| |
|---|
| [ImageFavicon](-image-favicon/index.md) |
| [UrlFavicon](-url-favicon/index.md) |

## Types

| Name | Summary |
|---|---|
| [ImageFavicon](-image-favicon/index.md) | [androidJvm]<br>data class [ImageFavicon](-image-favicon/index.md)(val icon: [Bitmap](https://developer.android.com/reference/kotlin/android/graphics/Bitmap.html), val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [FaviconSource](index.md) |
| [UrlFavicon](-url-favicon/index.md) | [androidJvm]<br>data class [UrlFavicon](-url-favicon/index.md)(val faviconUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [FaviconSource](index.md) |
