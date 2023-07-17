//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.passwordgeneration](../index.md)/[AutomaticSavedLoginsMonitor](index.md)

# AutomaticSavedLoginsMonitor

[androidJvm]\
interface [AutomaticSavedLoginsMonitor](index.md)

When password generation happens, we automatically create a login. This login might be later updated with more information when the form is submitted.

We need a way to monitor if a login was automatically created, for a specific tab, so we get data about it when form submitted.

By design, an automatically saved login is only monitored for the current page; when a navigation event happens it will be cleared.

## Functions

| Name | Summary |
|---|---|
| [clearAutoSavedLoginId](clear-auto-saved-login-id.md) | [androidJvm]<br>abstract fun [clearAutoSavedLoginId](clear-auto-saved-login-id.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)<br>Clears the automatically saved login ID for the current tab. |
| [getAutoSavedLoginId](get-auto-saved-login-id.md) | [androidJvm]<br>abstract fun [getAutoSavedLoginId](get-auto-saved-login-id.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)?<br>Retrieves the automatically saved login ID for the current tab, if any. |
| [setAutoSavedLoginId](set-auto-saved-login-id.md) | [androidJvm]<br>abstract fun [setAutoSavedLoginId](set-auto-saved-login-id.md)(value: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)<br>Sets the automatically saved login ID for the current tab. |
