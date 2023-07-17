//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)/[saveCredentials](save-credentials.md)

# saveCredentials

[androidJvm]\
abstract suspend fun [saveCredentials](save-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?

Save the given credentials for the given URL

#### Return

The saved credential if it saved successfully, otherwise null

#### Parameters

androidJvm

| | |
|---|---|
| rawUrl | Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
| credentials | The credentials to be saved. The ID can be null. |
