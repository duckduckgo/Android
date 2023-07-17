//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[WebsiteLoginDetailsWithCredentials](index.md)

# WebsiteLoginDetailsWithCredentials

[androidJvm]\
data class [WebsiteLoginDetailsWithCredentials](index.md)(val details: [WebsiteLoginDetails](../-website-login-details/index.md), val password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val notes: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null)

Public data class that wraps all data related to a website login. All succeeding l2 and above attributes should be added here directly.

[details](details.md) contains all l1 encrypted attributes [password](password.md) plain text password. [notes](notes.md) plain text notes associated to a login credential

## Constructors

| | |
|---|---|
| [WebsiteLoginDetailsWithCredentials](-website-login-details-with-credentials.md) | [androidJvm]<br>constructor(details: [WebsiteLoginDetails](../-website-login-details/index.md), password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, notes: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [details](details.md) | [androidJvm]<br>val [details](details.md): [WebsiteLoginDetails](../-website-login-details/index.md) |
| [notes](notes.md) | [androidJvm]<br>val [notes](notes.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [password](password.md) | [androidJvm]<br>val [password](password.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [toString](to-string.md) | [androidJvm]<br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
