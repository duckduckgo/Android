//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[updateWebsiteLoginDetailsWithCredentials](update-website-login-details-with-credentials.md)

# updateWebsiteLoginDetailsWithCredentials

[androidJvm]\
abstract suspend fun [updateWebsiteLoginDetailsWithCredentials](update-website-login-details-with-credentials.md)(websiteLoginDetailsWithCredentials: [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)): [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md)?

This method updates an existing [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) in the [SecureStorage](index.md). If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, nothing will be done.

#### Return

The updated credential if it saved successfully, otherwise null

#### Throws

| | |
|---|---|
| [SecureStorageException](../-secure-storage-exception/index.md) | if something went wrong while trying to perform the action. See type to get more info on the cause. |
