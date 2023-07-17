//[autoconsent-api](../../../index.md)/[com.duckduckgo.autoconsent.api](../index.md)/[AutoconsentCallback](index.md)

# AutoconsentCallback

[androidJvm]\
interface [AutoconsentCallback](index.md)

Public interface for the Autoconsent callback. It is required to be implemented and passed when calling addJsInterface and provides a useful set of callbacks.

## Functions

| Name | Summary |
|---|---|
| [onFirstPopUpHandled](on-first-pop-up-handled.md) | [androidJvm]<br>abstract fun [onFirstPopUpHandled](on-first-pop-up-handled.md)()<br>This method is called whenever a popup is handled for the first time. |
| [onPopUpHandled](on-pop-up-handled.md) | [androidJvm]<br>abstract fun [onPopUpHandled](on-pop-up-handled.md)(isCosmetic: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>This method is called whenever a popup is handled but not for the first time. |
| [onResultReceived](on-result-received.md) | [androidJvm]<br>abstract fun [onResultReceived](on-result-received.md)(consentManaged: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), optOutFailed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), selfTestFailed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), isCosmetic: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?)<br>This method is called whenever autoconsent has a result to be sent |
