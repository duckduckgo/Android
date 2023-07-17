//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[BrowserAutofill](index.md)

# BrowserAutofill

[androidJvm]\
interface [BrowserAutofill](index.md)

Public interface for accessing and configuring browser autofill functionality for a WebView instance

## Types

| Name | Summary |
|---|---|
| [Configurator](-configurator/index.md) | [androidJvm]<br>interface [Configurator](-configurator/index.md) |

## Functions

| Name | Summary |
|---|---|
| [acceptGeneratedPassword](accept-generated-password.md) | [androidJvm]<br>abstract fun [acceptGeneratedPassword](accept-generated-password.md)()<br>Informs the JS layer to use the generated password and fill it into the password field(s) |
| [addJsInterface](add-js-interface.md) | [androidJvm]<br>abstract fun [addJsInterface](add-js-interface.md)(webView: [WebView](https://developer.android.com/reference/kotlin/android/webkit/WebView.html), callback: [Callback](../-callback/index.md), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Adds the native->JS interface to the given WebView This should be called once per WebView where autofill is to be available in it |
| [cancelPendingAutofillRequestToChooseCredentials](cancel-pending-autofill-request-to-choose-credentials.md) | [androidJvm]<br>abstract fun [cancelPendingAutofillRequestToChooseCredentials](cancel-pending-autofill-request-to-choose-credentials.md)()<br>Cancels any ongoing autofill operations which would show the user the prompt to choose credentials This would only normally be needed if a user-interaction happened such that showing autofill prompt would be undesirable. |
| [injectCredentials](inject-credentials.md) | [androidJvm]<br>abstract fun [injectCredentials](inject-credentials.md)(credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?)<br>Communicates with the JS layer to pass the given credentials |
| [rejectGeneratedPassword](reject-generated-password.md) | [androidJvm]<br>abstract fun [rejectGeneratedPassword](reject-generated-password.md)()<br>Informs the JS layer not to use the generated password |
| [removeJsInterface](remove-js-interface.md) | [androidJvm]<br>abstract fun [removeJsInterface](remove-js-interface.md)()<br>Removes the JS interface as a clean-up. Recommended to call from onDestroy() of Fragment/Activity containing the WebView |
