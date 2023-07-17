//[secure-storage-api](../../index.md)/[com.duckduckgo.securestorage.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [SecureStorage](-secure-storage/index.md) | [androidJvm]<br>interface [SecureStorage](-secure-storage/index.md)<br>Public API for the secure storage feature |
| [SecureStorageException](-secure-storage-exception/index.md) | [androidJvm]<br>sealed class [SecureStorageException](-secure-storage-exception/index.md) : [Exception](https://developer.android.com/reference/kotlin/java/lang/Exception.html) |
| [WebsiteLoginDetails](-website-login-details/index.md) | [androidJvm]<br>data class [WebsiteLoginDetails](-website-login-details/index.md)(val domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null, val domainTitle: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val lastUpdatedMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null)<br>Public data class that wraps all data that should only be covered with l1 encryption. All attributes is in plain text. Also, all should not require user authentication to be decrypted. |
| [WebsiteLoginDetailsWithCredentials](-website-login-details-with-credentials/index.md) | [androidJvm]<br>data class [WebsiteLoginDetailsWithCredentials](-website-login-details-with-credentials/index.md)(val details: [WebsiteLoginDetails](-website-login-details/index.md), val password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val notes: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null)<br>Public data class that wraps all data related to a website login. All succeeding l2 and above attributes should be added here directly. |
