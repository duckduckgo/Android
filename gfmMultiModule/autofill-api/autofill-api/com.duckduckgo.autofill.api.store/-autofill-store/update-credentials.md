//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)/[updateCredentials](update-credentials.md)

# updateCredentials

[androidJvm]\
abstract suspend fun [updateCredentials](update-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md), updateType: [CredentialUpdateExistingCredentialsDialog.CredentialUpdateType](../../com.duckduckgo.autofill.api/-credential-update-existing-credentials-dialog/-credential-update-type/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?

Updates the credentials saved for the given URL

#### Return

The saved credential if it saved successfully, otherwise null

#### Parameters

androidJvm

| | |
|---|---|
| rawUrl | Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...) |
| credentials | The credentials to be updated. The ID can be null. |
| updateType | The type of update to perform, whether updating the username or password. |

[androidJvm]\
abstract suspend fun [updateCredentials](update-credentials.md)(credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?

Updates the given login credentials, replacing what was saved before for the credentials with the specified ID

#### Return

The saved credential if it saved successfully, otherwise null

#### Parameters

androidJvm

| | |
|---|---|
| credentials | The ID of the given credentials must match a saved credential for it to be updated. |
