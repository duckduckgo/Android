//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[UnprotectedTemporary](index.md)

# UnprotectedTemporary

[jvm]\
interface [UnprotectedTemporary](index.md)

Public interface for the Unprotected Temporary feature

## Properties

| Name | Summary |
|---|---|
| [unprotectedTemporaryExceptions](unprotected-temporary-exceptions.md) | [jvm]<br>abstract val [unprotectedTemporaryExceptions](unprotected-temporary-exceptions.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[UnprotectedTemporaryException](../-unprotected-temporary-exception/index.md)&gt;<br>The unprotected temporary exceptions list |

## Functions

| Name | Summary |
|---|---|
| [isAnException](is-an-exception.md) | [jvm]<br>abstract fun [isAnException](is-an-exception.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [url](is-an-exception.md) and returns `true` or `false` depending if the [url](is-an-exception.md) is in the unprotected temporary exceptions list |
