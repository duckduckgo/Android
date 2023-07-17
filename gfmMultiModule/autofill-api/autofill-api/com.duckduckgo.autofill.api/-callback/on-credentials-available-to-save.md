//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[Callback](index.md)/[onCredentialsAvailableToSave](on-credentials-available-to-save.md)

# onCredentialsAvailableToSave

[androidJvm]\
abstract suspend fun [onCredentialsAvailableToSave](on-credentials-available-to-save.md)(currentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md))

Called when there are login credentials available to be saved. When this is called, we'd typically want to prompt the user if they want to save the credentials.
