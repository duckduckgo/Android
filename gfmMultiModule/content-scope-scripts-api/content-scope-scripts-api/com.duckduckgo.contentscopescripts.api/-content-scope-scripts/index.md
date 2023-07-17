//[content-scope-scripts-api](../../../index.md)/[com.duckduckgo.contentscopescripts.api](../index.md)/[ContentScopeScripts](index.md)

# ContentScopeScripts

[androidJvm]\
interface [ContentScopeScripts](index.md)

Public interface for the Content Scope Scripts feature

## Functions

| Name | Summary |
|---|---|
| [addJsInterface](add-js-interface.md) | [androidJvm]<br>abstract fun [addJsInterface](add-js-interface.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html))<br>This method adds the JS interface for Content Scope Scripts to create a bridge between JS and our client. It requires a [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance. |
| [injectContentScopeScripts](inject-content-scope-scripts.md) | [androidJvm]<br>abstract fun [injectContentScopeScripts](inject-content-scope-scripts.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html))<br>This method injects the content scope scripts JS code into the [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html). It requires a [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance. |
| [sendMessage](send-message.md) | [androidJvm]<br>abstract fun [sendMessage](send-message.md)(message: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html))<br>This method sends a message to Content Scope Scripts. It requires a JSON message [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) and a [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html) instance. |
