//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.store](../index.md)/[AutofillStore](index.md)/[reinsertCredentials](reinsert-credentials.md)

# reinsertCredentials

[androidJvm]\
abstract suspend fun [reinsertCredentials](reinsert-credentials.md)(credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md))

Used to reinsert a credential that was previously deleted This supports the ability to give user a brief opportunity to 'undo' a deletion

This is similar to a normal save, except it will preserve the original ID and last modified time
