//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[Callback](index.md)/[onCredentialsAvailableToInject](on-credentials-available-to-inject.md)

# onCredentialsAvailableToInject

[androidJvm]\
abstract suspend fun [onCredentialsAvailableToInject](on-credentials-available-to-inject.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;, triggerType: [LoginTriggerType](../../com.duckduckgo.autofill.api.domain.app/-login-trigger-type/index.md))

Called when we've determined we have credentials we can offer to autofill for the user. When this is called, we should present the list to the user for them to choose which one, if any, to autofill.
