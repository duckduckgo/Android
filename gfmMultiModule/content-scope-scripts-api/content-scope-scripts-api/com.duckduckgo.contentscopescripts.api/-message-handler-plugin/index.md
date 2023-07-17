//[content-scope-scripts-api](../../../index.md)/[com.duckduckgo.contentscopescripts.api](../index.md)/[MessageHandlerPlugin](index.md)

# MessageHandlerPlugin

[androidJvm]\
interface [MessageHandlerPlugin](index.md)

## Properties

| Name | Summary |
|---|---|
| [supportedTypes](supported-types.md) | [androidJvm]<br>abstract val [supportedTypes](supported-types.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |

## Functions

| Name | Summary |
|---|---|
| [process](process.md) | [androidJvm]<br>abstract fun [process](process.md)(messageType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), jsonString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), responseListener: [ResponseListener](../-response-listener/index.md)) |
