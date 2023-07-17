//[request-filterer-api](../../../index.md)/[com.duckduckgo.request.filterer.api](../index.md)/[RequestFilterer](index.md)

# RequestFilterer

[androidJvm]\
interface [RequestFilterer](index.md)

Public interface for the Request Filterer feature

## Functions

| Name | Summary |
|---|---|
| [registerOnPageCreated](register-on-page-created.md) | [androidJvm]<br>abstract fun [registerOnPageCreated](register-on-page-created.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>This method takes a [url](register-on-page-created.md) and registers it internally. This method must be used before using `shouldFilterOutRequest` to correctly register the different page created events. |
| [shouldFilterOutRequest](should-filter-out-request.md) | [androidJvm]<br>abstract fun [shouldFilterOutRequest](should-filter-out-request.md)(request: [WebResourceRequest](https://developer.android.com/reference/kotlin/android/webkit/WebResourceRequest.html), documentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [request](should-filter-out-request.md) and a [documentUrl](should-filter-out-request.md) to calculate if the request should be filtered out or not. |
