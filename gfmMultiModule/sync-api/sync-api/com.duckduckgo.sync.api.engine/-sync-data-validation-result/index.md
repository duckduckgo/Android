//[sync-api](../../../index.md)/[com.duckduckgo.sync.api.engine](../index.md)/[SyncDataValidationResult](index.md)

# SyncDataValidationResult

sealed class [SyncDataValidationResult](index.md)&lt;out [R](index.md)&gt;

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [Error](-error/index.md) |

## Types

| Name | Summary |
|---|---|
| [Error](-error/index.md) | [androidJvm]<br>data class [Error](-error/index.md)(val reason: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [SyncDataValidationResult](index.md)&lt;[Nothing](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-nothing/index.html)&gt; |
| [Success](-success/index.md) | [androidJvm]<br>data class [Success](-success/index.md)&lt;out [T](-success/index.md)&gt;(val data: [T](-success/index.md)) : [SyncDataValidationResult](index.md)&lt;[T](-success/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [toString](to-string.md) | [androidJvm]<br>open override fun [toString](to-string.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
