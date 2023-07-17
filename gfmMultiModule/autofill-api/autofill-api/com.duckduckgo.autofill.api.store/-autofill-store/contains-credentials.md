//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)/[containsCredentials](contains-credentials.md)

# containsCredentials

[androidJvm]\
abstract suspend fun [containsCredentials](contains-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [AutofillStore.ContainsCredentialsResult](-contains-credentials-result/index.md)

Searches the saved login credentials for a match to the given URL, username and password This can be used to determine if we need to prompt the user to update a saved credential

#### Return

The match type, which might indicate there was an exact match, a partial match etc...
