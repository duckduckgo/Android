//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[addWebsiteLoginDetailsWithCredentials](add-website-login-details-with-credentials.md)

# addWebsiteLoginDetailsWithCredentials

[androidJvm]\
abstract suspend fun [addWebsiteLoginDetailsWithCredentials](add-website-login-details-with-credentials.md)(websiteLoginDetailsWithCredentials: [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)): [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)?

This method adds a raw plaintext [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) into the [SecureStorage](index.md). If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, nothing will be done.

#### Return

The saved credential if it saved successfully, otherwise null

#### Throws

| | |
|---|---|
| [SecureStorageException](../-secure-storage-exception/index.md) | if something went wrong while trying to perform the action. See type to get more info on the cause. |
