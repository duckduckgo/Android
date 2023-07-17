//[autofill-api](../../../../index.md)/[com.duckduckgo.autofill.api](../../index.md)/[BrowserAutofill](../index.md)/[Configurator](index.md)/[configureAutofillForCurrentPage](configure-autofill-for-current-page.md)

# configureAutofillForCurrentPage

[androidJvm]\
abstract fun [configureAutofillForCurrentPage](configure-autofill-for-current-page.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)

Configures autofill for the current webpage. This should be called once per page load (e.g., onPageStarted())

Responsible for injecting the required autofill configuration to the JS layer
