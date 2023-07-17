//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.domain.app](../index.md)/[LoginCredentials](index.md)

# LoginCredentials

[androidJvm]\
data class [LoginCredentials](index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null, val domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val domainTitle: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val notes: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val lastUpdatedMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null) : [Parcelable](https://developer.android.com/reference/kotlin/android/os/Parcelable.html)

Representation of login credentials used for autofilling into the browser.

## Constructors

| | |
|---|---|
| [LoginCredentials](-login-credentials.md) | [androidJvm]<br>constructor(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null, domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, password: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, domainTitle: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, notes: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, lastUpdatedMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [domain](domain.md) | [androidJvm]<br>val [domain](domain.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [domainTitle](domain-title.md) | [androidJvm]<br>val [domainTitle](domain-title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [id](id.md) | [androidJvm]<br>val [id](id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null |
| [lastUpdatedMillis](last-updated-millis.md) | [androidJvm]<br>val [lastUpdatedMillis](last-updated-millis.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)? = null |
| [notes](notes.md) | [androidJvm]<br>val [notes](notes.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [password](password.md) | [androidJvm]<br>val [password](password.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
| [username](username.md) | [androidJvm]<br>val [username](username.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [describeContents](index.md#-1578325224%2FFunctions%2F1052887353) | [androidJvm]<br>abstract fun [describeContents](index.md#-1578325224%2FFunctions%2F1052887353)(): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [toString](to-string.md) | [androidJvm]<br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [writeToParcel](index.md#-1754457655%2FFunctions%2F1052887353) | [androidJvm]<br>abstract fun [writeToParcel](index.md#-1754457655%2FFunctions%2F1052887353)(p0: [Parcel](https://developer.android.com/reference/kotlin/android/os/Parcel.html), p1: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
