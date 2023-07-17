//[autoconsent-api](../../../index.md)/[com.duckduckgo.autoconsent.api](../index.md)/[Autoconsent](index.md)

# Autoconsent

[androidJvm]\
interface [Autoconsent](index.md)

Public interface for the Autoconsent (CMP) feature

## Functions

| Name | Summary |
|---|---|
| [addJsInterface](add-js-interface.md) | [androidJvm]<br>abstract fun [addJsInterface](add-js-interface.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html), autoconsentCallback: [AutoconsentCallback](../-autoconsent-callback/index.md))<br>This method adds the JS interface for autoconsent to create a bridge between JS and our client. It requires a [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance and an [AutoconsentCallback](../-autoconsent-callback/index.md). |
| [changeSetting](change-setting.md) | [androidJvm]<br>abstract fun [changeSetting](change-setting.md)(setting: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>This method enables or disables autoconsent setting depending on the value passed. |
| [firstPopUpHandled](first-pop-up-handled.md) | [androidJvm]<br>abstract fun [firstPopUpHandled](first-pop-up-handled.md)()<br>This method stores a value so autoconsent knows the first pop-up was already handled. |
| [injectAutoconsent](inject-autoconsent.md) | [androidJvm]<br>abstract fun [injectAutoconsent](inject-autoconsent.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>This method injects the JS code needed to run autoconsent. It requires a [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance and the URL where the code will be injected. |
| [isSettingEnabled](is-setting-enabled.md) | [androidJvm]<br>abstract fun [isSettingEnabled](is-setting-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [setAutoconsentOptIn](set-autoconsent-opt-in.md) | [androidJvm]<br>abstract fun [setAutoconsentOptIn](set-autoconsent-opt-in.md)()<br>This method sets autoconsent to opt in mode. |
| [setAutoconsentOptOut](set-autoconsent-opt-out.md) | [androidJvm]<br>abstract fun [setAutoconsentOptOut](set-autoconsent-opt-out.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html))<br>This method sends and opt out message to autoconsent on the given [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance to set the opt out mode. |
