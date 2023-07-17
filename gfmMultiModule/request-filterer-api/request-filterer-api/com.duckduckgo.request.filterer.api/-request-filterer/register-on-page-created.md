//[request-filterer-api](../../../index.md)/[com.duckduckgo.request.filterer.api](../index.md)/[RequestFilterer](index.md)/[registerOnPageCreated](register-on-page-created.md)

# registerOnPageCreated

[androidJvm]\
abstract fun [registerOnPageCreated](register-on-page-created.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

This method takes a [url](register-on-page-created.md) and registers it internally. This method must be used before using `shouldFilterOutRequest` to correctly register the different page created events.
