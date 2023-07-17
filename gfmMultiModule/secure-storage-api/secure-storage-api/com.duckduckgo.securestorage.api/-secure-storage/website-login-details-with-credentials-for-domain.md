//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[websiteLoginDetailsWithCredentialsForDomain](website-login-details-with-credentials-for-domain.md)

# websiteLoginDetailsWithCredentialsForDomain

[androidJvm]\
abstract suspend fun [websiteLoginDetailsWithCredentialsForDomain](website-login-details-with-credentials-for-domain.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)&gt;&gt;

This method returns the [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) with the [domain](website-login-details-with-credentials-for-domain.md) stored in the [SecureStorage](index.md). This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed. If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, an empty flow will be emitted.

#### Return

Flow<List<WebsiteLoginDetailsWithCredentials>> a flow emitting a List of plain text WebsiteLoginDetailsWithCredentials stored in SecureStorage containing the plaintext password

#### Throws

| | |
|---|---|
| [SecureStorageException](../-secure-storage-exception/index.md) | if something went wrong while trying to perform the action. See type to get more info on the cause. |
