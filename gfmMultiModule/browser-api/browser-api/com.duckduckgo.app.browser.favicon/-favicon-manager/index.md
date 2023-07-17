//[browser-api](../../../index.md)/[com.duckduckgo.app.browser.favicon](../index.md)/[FaviconManager](index.md)

# FaviconManager

[androidJvm]\
interface [FaviconManager](index.md)

## Functions

| Name | Summary |
|---|---|
| [deleteAllTemp](delete-all-temp.md) | [androidJvm]<br>abstract suspend fun [deleteAllTemp](delete-all-temp.md)() |
| [deleteOldTempFavicon](delete-old-temp-favicon.md) | [androidJvm]<br>abstract suspend fun [deleteOldTempFavicon](delete-old-temp-favicon.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), path: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?) |
| [deletePersistedFavicon](delete-persisted-favicon.md) | [androidJvm]<br>abstract suspend fun [deletePersistedFavicon](delete-persisted-favicon.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [generateDefaultFavicon](generate-default-favicon.md) | [androidJvm]<br>abstract fun [generateDefaultFavicon](generate-default-favicon.md)(placeholder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Drawable](https://developer.android.com/reference/kotlin/android/graphics/drawable/Drawable.html)<br>Generates a drawable which can be used as a placeholder for a favicon when a real one cannot be found |
| [loadFromDisk](load-from-disk.md) | [androidJvm]<br>abstract suspend fun [loadFromDisk](load-from-disk.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Bitmap](https://developer.android.com/reference/kotlin/android/graphics/Bitmap.html)? |
| [loadFromDiskWithParams](load-from-disk-with-params.md) | [androidJvm]<br>abstract suspend fun [loadFromDiskWithParams](load-from-disk-with-params.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), cornerRadius: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), width: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), height: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [Bitmap](https://developer.android.com/reference/kotlin/android/graphics/Bitmap.html)? |
| [loadToViewFromLocalWithPlaceholder](load-to-view-from-local-with-placeholder.md) | [androidJvm]<br>abstract suspend fun [loadToViewFromLocalWithPlaceholder](load-to-view-from-local-with-placeholder.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), view: [ImageView](https://developer.android.com/reference/kotlin/android/widget/ImageView.html), placeholder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) |
| [persistCachedFavicon](persist-cached-favicon.md) | [androidJvm]<br>abstract suspend fun [persistCachedFavicon](persist-cached-favicon.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [storeFavicon](store-favicon.md) | [androidJvm]<br>abstract suspend fun [storeFavicon](store-favicon.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), faviconSource: [FaviconSource](../-favicon-source/index.md)): [File](https://developer.android.com/reference/kotlin/java/io/File.html)? |
| [tryFetchFaviconForUrl](try-fetch-favicon-for-url.md) | [androidJvm]<br>abstract suspend fun [tryFetchFaviconForUrl](try-fetch-favicon-for-url.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [File](https://developer.android.com/reference/kotlin/java/io/File.html)? |
