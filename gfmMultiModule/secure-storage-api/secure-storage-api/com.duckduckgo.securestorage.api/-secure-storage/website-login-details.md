//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[websiteLoginDetails](website-login-details.md)

# websiteLoginDetails

[androidJvm]\
abstract suspend fun [websiteLoginDetails](website-login-details.md)(): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[WebsiteLoginDetails](../-website-login-details/index.md)&gt;&gt;

This method returns all [WebsiteLoginDetails](../-website-login-details/index.md) stored in the [SecureStorage](index.md). Only L1 encrypted data is returned by these function. This is best use when the need is only to access non-sensitive data. If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, an empty flow will be emitted.

#### Return

Flow<List<WebsiteLoginDetails>> a flow containing a List of plain text WebsiteLoginDetails stored in SecureStorage.
