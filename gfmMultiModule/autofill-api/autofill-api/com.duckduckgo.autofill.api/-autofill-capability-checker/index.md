//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[AutofillCapabilityChecker](index.md)

# AutofillCapabilityChecker

[androidJvm]\
interface [AutofillCapabilityChecker](index.md)

Used to check the status of various Autofill features.

Whether autofill features are enabled depends on a variety of inputs. This class provides a single way to query the status of all of them.

## Functions

| Name | Summary |
|---|---|
| [canAccessCredentialManagementScreen](can-access-credential-management-screen.md) | [androidJvm]<br>abstract suspend fun [canAccessCredentialManagementScreen](can-access-credential-management-screen.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether a user can access the credential management screen. |
| [canGeneratePasswordFromWebView](can-generate-password-from-web-view.md) | [androidJvm]<br>abstract suspend fun [canGeneratePasswordFromWebView](can-generate-password-from-web-view.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether autofill can generate a password into a WebView |
| [canInjectCredentialsToWebView](can-inject-credentials-to-web-view.md) | [androidJvm]<br>abstract suspend fun [canInjectCredentialsToWebView](can-inject-credentials-to-web-view.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether autofill can inject credentials into a WebView. |
| [canSaveCredentialsFromWebView](can-save-credentials-from-web-view.md) | [androidJvm]<br>abstract suspend fun [canSaveCredentialsFromWebView](can-save-credentials-from-web-view.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether autofill can save credentials from a WebView. |
| [isAutofillEnabledByConfiguration](is-autofill-enabled-by-configuration.md) | [androidJvm]<br>abstract suspend fun [isAutofillEnabledByConfiguration](is-autofill-enabled-by-configuration.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether autofill is configured to be enabled. This is a configuration value, not a user preference. |
