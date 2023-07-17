//[browser-api](../../../index.md)/[com.duckduckgo.app.browser.favicon](../index.md)/[FaviconManager](index.md)/[generateDefaultFavicon](generate-default-favicon.md)

# generateDefaultFavicon

[androidJvm]\
abstract fun [generateDefaultFavicon](generate-default-favicon.md)(placeholder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Drawable](https://developer.android.com/reference/kotlin/android/graphics/drawable/Drawable.html)

Generates a drawable which can be used as a placeholder for a favicon when a real one cannot be found

#### Parameters

androidJvm

| | |
|---|---|
| placeholder | the placeholder text to be used. if null, the placeholder letter will be extracted from the domain |
| domain | the domain of the site for which the favicon is being generated, used to generate background color |
