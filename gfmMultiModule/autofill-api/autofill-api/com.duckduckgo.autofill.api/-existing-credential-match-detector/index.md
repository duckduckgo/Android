//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[ExistingCredentialMatchDetector](index.md)

# ExistingCredentialMatchDetector

[androidJvm]\
interface [ExistingCredentialMatchDetector](index.md)

Used to determine if the given credential details exist in the autofill storage

There are times when the UI from the main app will need to prompt the user if they want to update saved details. We can only show that prompt if we've first determined there is an existing partial match in need of an update.

## Functions

| Name | Summary |
|---|---|
| [determine](determine.md) | [androidJvm]<br>abstract suspend fun [determine](determine.md)(currentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [AutofillStore.ContainsCredentialsResult](../../com.duckduckgo.autofill.api.store/-autofill-store/-contains-credentials-result/index.md) |
