//[secure-storage-api](../../../index.md)/[com.duckduckgo.securestorage.api](../index.md)/[SecureStorageException](index.md)

# SecureStorageException

sealed class [SecureStorageException](index.md) : [Exception](https://developer.android.com/reference/kotlin/java/lang/Exception.html)

#### Inheritors

| |
|---|
| [UserNotAuthenticatedException](-user-not-authenticated-exception/index.md) |
| [InternalSecureStorageException](-internal-secure-storage-exception/index.md) |

## Types

| Name | Summary |
|---|---|
| [InternalSecureStorageException](-internal-secure-storage-exception/index.md) | [androidJvm]<br>data class [InternalSecureStorageException](-internal-secure-storage-exception/index.md)(val message: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val cause: [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)? = null) : [SecureStorageException](index.md) |
| [UserNotAuthenticatedException](-user-not-authenticated-exception/index.md) | [androidJvm]<br>data class [UserNotAuthenticatedException](-user-not-authenticated-exception/index.md)(val message: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [SecureStorageException](index.md)<br>Public data class exception that is thrown when a method that requires user authentication is accessed by a non authenticated user. |

## Properties

| Name | Summary |
|---|---|
| [cause](-user-not-authenticated-exception/index.md#-654012527%2FProperties%2F-1431682644) | [androidJvm]<br>open val [cause](-user-not-authenticated-exception/index.md#-654012527%2FProperties%2F-1431682644): [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)? |
| [message](index.md#1824300659%2FProperties%2F-1431682644) | [androidJvm]<br>open val [message](index.md#1824300659%2FProperties%2F-1431682644): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |

## Functions

| Name | Summary |
|---|---|
| [addSuppressed](-internal-secure-storage-exception/index.md#282858770%2FFunctions%2F-1431682644) | [androidJvm]<br>fun [addSuppressed](-internal-secure-storage-exception/index.md#282858770%2FFunctions%2F-1431682644)(p0: [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)) |
| [fillInStackTrace](-internal-secure-storage-exception/index.md#-1102069925%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [fillInStackTrace](-internal-secure-storage-exception/index.md#-1102069925%2FFunctions%2F-1431682644)(): [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html) |
| [getLocalizedMessage](-internal-secure-storage-exception/index.md#1043865560%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [getLocalizedMessage](-internal-secure-storage-exception/index.md#1043865560%2FFunctions%2F-1431682644)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [getStackTrace](-internal-secure-storage-exception/index.md#2050903719%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [getStackTrace](-internal-secure-storage-exception/index.md#2050903719%2FFunctions%2F-1431682644)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[StackTraceElement](https://developer.android.com/reference/kotlin/java/lang/StackTraceElement.html)&gt; |
| [getSuppressed](-internal-secure-storage-exception/index.md#672492560%2FFunctions%2F-1431682644) | [androidJvm]<br>fun [getSuppressed](-internal-secure-storage-exception/index.md#672492560%2FFunctions%2F-1431682644)(): [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)&gt; |
| [initCause](-internal-secure-storage-exception/index.md#-418225042%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [initCause](-internal-secure-storage-exception/index.md#-418225042%2FFunctions%2F-1431682644)(p0: [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)): [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html) |
| [printStackTrace](-internal-secure-storage-exception/index.md#-1769529168%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [printStackTrace](-internal-secure-storage-exception/index.md#-1769529168%2FFunctions%2F-1431682644)()<br>open fun [printStackTrace](-internal-secure-storage-exception/index.md#1841853697%2FFunctions%2F-1431682644)(p0: [PrintStream](https://developer.android.com/reference/kotlin/java/io/PrintStream.html))<br>open fun [printStackTrace](-internal-secure-storage-exception/index.md#1175535278%2FFunctions%2F-1431682644)(p0: [PrintWriter](https://developer.android.com/reference/kotlin/java/io/PrintWriter.html)) |
| [setStackTrace](-internal-secure-storage-exception/index.md#2135801318%2FFunctions%2F-1431682644) | [androidJvm]<br>open fun [setStackTrace](-internal-secure-storage-exception/index.md#2135801318%2FFunctions%2F-1431682644)(p0: [Array](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-array/index.html)&lt;[StackTraceElement](https://developer.android.com/reference/kotlin/java/lang/StackTraceElement.html)&gt;) |
