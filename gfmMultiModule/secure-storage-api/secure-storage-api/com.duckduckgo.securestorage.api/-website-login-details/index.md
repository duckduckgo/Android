//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[WebsiteLoginDetails](index.md)

# WebsiteLoginDetails

[androidJvm]\
data class [WebsiteLoginDetails](index.md)(val domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null, val domainTitle: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val lastUpdatedMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null)

Public data class that wraps all data that should only be covered with l1 encryption. All attributes is in plain text. Also, all should not require user authentication to be decrypted.

[domain](domain.md) url/name associated to a website login. [username](username.md) used to populate the username fields in a login [id](id.md) database id associated to the website login [domainTitle](domain-title.md) title associated to the login [lastUpdatedMillis](last-updated-millis.md) time in milliseconds when the credential was last updated

## Constructors

| | |
|---|---|
| [WebsiteLoginDetails](-website-login-details.md) | [androidJvm]<br>constructor(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null, domainTitle: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, lastUpdatedMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [domain](domain.md) | [androidJvm]<br>val [domain](domain.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [domainTitle](domain-title.md) | [androidJvm]<br>val [domainTitle](domain-title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [id](id.md) | [androidJvm]<br>val [id](id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null |
| [lastUpdatedMillis](last-updated-millis.md) | [androidJvm]<br>val [lastUpdatedMillis](last-updated-millis.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null |
| [username](username.md) | [androidJvm]<br>val [username](username.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [toString](to-string.md) | [androidJvm]<br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
