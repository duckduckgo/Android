//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorage](index.md)/[websiteLoginDetailsForDomain](website-login-details-for-domain.md)

# websiteLoginDetailsForDomain

[androidJvm]\
abstract suspend fun [websiteLoginDetailsForDomain](website-login-details-for-domain.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[WebsiteLoginDetails](../-website-login-details/index.md)&gt;&gt;

This method returns all [WebsiteLoginDetails](../-website-login-details/index.md) with the [domain](website-login-details-for-domain.md) stored in the [SecureStorage](index.md). Only L1 encrypted data is returned by these function. This is best use when the need is only to access non-sensitive data. If [canAccessSecureStorage](can-access-secure-storage.md) is false when this is invoked, an empty flow will be emitted.

#### Return

Flow<List<WebsiteLoginDetails>> a flow emitting a List of plain text WebsiteLoginDetails stored in SecureStorage.
