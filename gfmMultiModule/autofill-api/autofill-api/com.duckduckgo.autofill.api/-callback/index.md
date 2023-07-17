//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[Callback](index.md)

# Callback

[androidJvm]\
interface [Callback](index.md)

Browser Autofill callbacks

## Functions

| Name | Summary |
|---|---|
| [noCredentialsAvailable](no-credentials-available.md) | [androidJvm]<br>abstract fun [noCredentialsAvailable](no-credentials-available.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Called when we've been asked which credentials we have available to autofill, but the answer is none. |
| [onCredentialsAvailableToInject](on-credentials-available-to-inject.md) | [androidJvm]<br>abstract suspend fun [onCredentialsAvailableToInject](on-credentials-available-to-inject.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;, triggerType: [LoginTriggerType](../../com.duckduckgo.autofill.api.domain.app/-login-trigger-type/index.md))<br>Called when we've determined we have credentials we can offer to autofill for the user. When this is called, we should present the list to the user for them to choose which one, if any, to autofill. |
| [onCredentialsAvailableToSave](on-credentials-available-to-save.md) | [androidJvm]<br>abstract suspend fun [onCredentialsAvailableToSave](on-credentials-available-to-save.md)(currentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md))<br>Called when there are login credentials available to be saved. When this is called, we'd typically want to prompt the user if they want to save the credentials. |
| [onCredentialsSaved](on-credentials-saved.md) | [androidJvm]<br>abstract fun [onCredentialsSaved](on-credentials-saved.md)(savedCredentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md))<br>Called when credentials have been saved, and we want to show the user some visual confirmation. |
| [onGeneratedPasswordAvailableToUse](on-generated-password-available-to-use.md) | [androidJvm]<br>abstract suspend fun [onGeneratedPasswordAvailableToUse](on-generated-password-available-to-use.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, generatedPassword: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Called when we've generated a password for the user, and we want to offer it to them to use. When this is called, we should present the generated password to the user for them to choose whether to use it or not. |
