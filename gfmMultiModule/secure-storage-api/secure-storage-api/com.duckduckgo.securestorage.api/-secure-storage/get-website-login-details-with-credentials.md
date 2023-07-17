//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[getWebsiteLoginDetailsWithCredentials](get-website-login-details-with-credentials.md)

# getWebsiteLoginDetailsWithCredentials

[androidJvm]\
abstract suspend fun [getWebsiteLoginDetailsWithCredentials](get-website-login-details-with-credentials.md)(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)): [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)?

This method returns the [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) with the [id](get-website-login-details-with-credentials.md) stored in the [SecureStorage](index.md). This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed. If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, null will be returned.

#### Return

[WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) containing the plaintext password

#### Throws

| | |
|---|---|
| [SecureStorageException](../-secure-storage-exception/index.md) | if something went wrong while trying to perform the action. See type to get more info on the cause. |
