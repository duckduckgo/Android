//[request-filterer-api](../../../index.md)/[com.duckduckgo.request.filterer.api](../index.md)/[RequestFilterer](index.md)/[shouldFilterOutRequest](should-filter-out-request.md)

# shouldFilterOutRequest

[androidJvm]\
abstract fun [shouldFilterOutRequest](should-filter-out-request.md)(request: [WebResourceRequest](https://developer.android.com/reference/kotlin/android/webkit/WebResourceRequest.html), documentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This method takes a [request](should-filter-out-request.md) and a [documentUrl](should-filter-out-request.md) to calculate if the request should be filtered out or not.

#### Return

`true` if the request should be filtered or `false` if it shouldn't
