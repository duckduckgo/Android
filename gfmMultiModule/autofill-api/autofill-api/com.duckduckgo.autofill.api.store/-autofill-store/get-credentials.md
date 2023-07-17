//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)/[getCredentials](get-credentials.md)

# getCredentials

[androidJvm]\
abstract suspend fun [getCredentials](get-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;

Find saved credentials for the given URL, returning an empty list where no matches are found

#### Parameters

androidJvm

| | |
|---|---|
| rawUrl | Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
