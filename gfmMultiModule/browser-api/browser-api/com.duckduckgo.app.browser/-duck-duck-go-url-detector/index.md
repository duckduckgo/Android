//[browser-api](../../../index.md)/[com.duckduckgo.app.browser](../index.md)/[DuckDuckGoUrlDetector](index.md)

# DuckDuckGoUrlDetector

[androidJvm]\
interface [DuckDuckGoUrlDetector](index.md)

Public interface for DuckDuckGoUrlDetector

## Functions

| Name | Summary |
|---|---|
| [extractQuery](extract-query.md) | [androidJvm]<br>abstract fun [extractQuery](extract-query.md)(uriString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>This method takes a [uriString](extract-query.md) and returns a String? |
| [extractVertical](extract-vertical.md) | [androidJvm]<br>abstract fun [extractVertical](extract-vertical.md)(uriString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>This method takes a [uriString](extract-vertical.md) and returns a String? |
| [isDuckDuckGoEmailUrl](is-duck-duck-go-email-url.md) | [androidJvm]<br>abstract fun [isDuckDuckGoEmailUrl](is-duck-duck-go-email-url.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [url](is-duck-duck-go-email-url.md) and returns `true` or `false`. |
| [isDuckDuckGoQueryUrl](is-duck-duck-go-query-url.md) | [androidJvm]<br>abstract fun [isDuckDuckGoQueryUrl](is-duck-duck-go-query-url.md)(uri: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [uri](is-duck-duck-go-query-url.md) and returns `true` or `false`. |
| [isDuckDuckGoStaticUrl](is-duck-duck-go-static-url.md) | [androidJvm]<br>abstract fun [isDuckDuckGoStaticUrl](is-duck-duck-go-static-url.md)(uri: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [uri](is-duck-duck-go-static-url.md) and returns `true` or `false`. |
| [isDuckDuckGoUrl](is-duck-duck-go-url.md) | [androidJvm]<br>abstract fun [isDuckDuckGoUrl](is-duck-duck-go-url.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [url](is-duck-duck-go-url.md) and returns `true` or `false`. |
| [isDuckDuckGoVerticalUrl](is-duck-duck-go-vertical-url.md) | [androidJvm]<br>abstract fun [isDuckDuckGoVerticalUrl](is-duck-duck-go-vertical-url.md)(uri: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [uri](is-duck-duck-go-vertical-url.md) and returns `true` or `false`. |
