//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)

# AutofillStore

[androidJvm]\
interface [AutofillStore](index.md)

APIs for accessing and updating saved autofill data

## Types

| Name | Summary |
|---|---|
| [ContainsCredentialsResult](-contains-credentials-result/index.md) | [androidJvm]<br>interface [ContainsCredentialsResult](-contains-credentials-result/index.md)<br>Possible match types returned when searching for the presence of credentials |

## Properties

| Name | Summary |
|---|---|
| [autofillAvailable](autofill-available.md) | [androidJvm]<br>abstract val [autofillAvailable](autofill-available.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Determines if the autofill feature is available for the user |
| [autofillDeclineCount](autofill-decline-count.md) | [androidJvm]<br>abstract var [autofillDeclineCount](autofill-decline-count.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)<br>A count of the number of autofill declines the user has made, persisted across all sessions. Used to determine whether we should prompt a user new to autofill to disable it if they don't appear to want it enabled |
| [autofillEnabled](autofill-enabled.md) | [androidJvm]<br>abstract var [autofillEnabled](autofill-enabled.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Global toggle for determining / setting if autofill is enabled |
| [hasEverBeenPromptedToSaveLogin](has-ever-been-prompted-to-save-login.md) | [androidJvm]<br>abstract var [hasEverBeenPromptedToSaveLogin](has-ever-been-prompted-to-save-login.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Used to determine if a user has ever been prompted to save a login (note: prompted to save, not necessarily saved) Defaults to false, and will be set to true after the user has been shown a prompt to save a login |
| [monitorDeclineCounts](monitor-decline-counts.md) | [androidJvm]<br>abstract var [monitorDeclineCounts](monitor-decline-counts.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Whether to monitor autofill decline counts or not Used to determine whether we should actively detect when a user new to autofill doesn't appear to want it enabled |

## Functions

| Name | Summary |
|---|---|
| [containsCredentials](contains-credentials.md) | [androidJvm]<br>abstract suspend fun [containsCredentials](contains-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [AutofillStore.ContainsCredentialsResult](-contains-credentials-result/index.md)<br>Searches the saved login credentials for a match to the given URL, username and password This can be used to determine if we need to prompt the user to update a saved credential |
| [deleteCredentials](delete-credentials.md) | [androidJvm]<br>abstract suspend fun [deleteCredentials](delete-credentials.md)(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?<br>Deletes the credential with the given ID |
| [getAllCredentials](get-all-credentials.md) | [androidJvm]<br>abstract suspend fun [getAllCredentials](get-all-credentials.md)(): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;&gt;<br>Returns the full list of stored login credentials |
| [getCredentialCount](get-credential-count.md) | [androidJvm]<br>abstract suspend fun [getCredentialCount](get-credential-count.md)(): Flow&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;<br>Returns a count of how many credentials are stored |
| [getCredentials](get-credentials.md) | [androidJvm]<br>abstract suspend fun [getCredentials](get-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;<br>Find saved credentials for the given URL, returning an empty list where no matches are found |
| [getCredentialsWithId](get-credentials-with-id.md) | [androidJvm]<br>abstract suspend fun [getCredentialsWithId](get-credentials-with-id.md)(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?<br>Find saved credential for the given id |
| [reinsertCredentials](reinsert-credentials.md) | [androidJvm]<br>abstract suspend fun [reinsertCredentials](reinsert-credentials.md)(credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md))<br>Used to reinsert a credential that was previously deleted This supports the ability to give user a brief opportunity to 'undo' a deletion |
| [saveCredentials](save-credentials.md) | [androidJvm]<br>abstract suspend fun [saveCredentials](save-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?<br>Save the given credentials for the given URL |
| [updateCredentials](update-credentials.md) | [androidJvm]<br>abstract suspend fun [updateCredentials](update-credentials.md)(credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?<br>Updates the given login credentials, replacing what was saved before for the credentials with the specified ID<br>[androidJvm]<br>abstract suspend fun [updateCredentials](update-credentials.md)(rawUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md), updateType: [CredentialUpdateExistingCredentialsDialog.CredentialUpdateType](../../com.duckduckgo.autofill.api/-credential-update-existing-credentials-dialog/-credential-update-type/index.md)): [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)?<br>Updates the credentials saved for the given URL |
