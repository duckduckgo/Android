//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[deleteWebsiteLoginDetailsWithCredentials](delete-website-login-details-with-credentials.md)

# deleteWebsiteLoginDetailsWithCredentials

[androidJvm]\
abstract suspend fun [deleteWebsiteLoginDetailsWithCredentials](delete-website-login-details-with-credentials.md)(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))

This method removes an existing [WebsiteLoginDetailsWithCredentials](../-website-login-details-with-credentials/index.md) associated with an [id](delete-website-login-details-with-credentials.md) from the [SecureStorage](index.md). If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, nothing will be done.
