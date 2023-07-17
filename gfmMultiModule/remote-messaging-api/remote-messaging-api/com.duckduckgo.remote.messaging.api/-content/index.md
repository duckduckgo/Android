//[remote-messaging-api](../../../index.md)/[com.duckduckgo.remote.messaging.api](../index.md)/[Content](index.md)

# Content

sealed class [Content](index.md)

#### Inheritors

| |
|---|
| [Small](-small/index.md) |
| [Medium](-medium/index.md) |
| [BigSingleAction](-big-single-action/index.md) |
| [BigTwoActions](-big-two-actions/index.md) |

## Types

| Name | Summary |
|---|---|
| [BigSingleAction](-big-single-action/index.md) | [jvm]<br>data class [BigSingleAction](-big-single-action/index.md)(val titleText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val descriptionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val placeholder: [Content.Placeholder](-placeholder/index.md), val primaryActionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val primaryAction: [Action](../-action/index.md)) : [Content](index.md) |
| [BigTwoActions](-big-two-actions/index.md) | [jvm]<br>data class [BigTwoActions](-big-two-actions/index.md)(val titleText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val descriptionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val placeholder: [Content.Placeholder](-placeholder/index.md), val primaryActionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val primaryAction: [Action](../-action/index.md), val secondaryActionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val secondaryAction: [Action](../-action/index.md)) : [Content](index.md) |
| [Medium](-medium/index.md) | [jvm]<br>data class [Medium](-medium/index.md)(val titleText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val descriptionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val placeholder: [Content.Placeholder](-placeholder/index.md)) : [Content](index.md) |
| [MessageType](-message-type/index.md) | [jvm]<br>enum [MessageType](-message-type/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[Content.MessageType](-message-type/index.md)&gt; |
| [Placeholder](-placeholder/index.md) | [jvm]<br>enum [Placeholder](-placeholder/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[Content.Placeholder](-placeholder/index.md)&gt; |
| [Small](-small/index.md) | [jvm]<br>data class [Small](-small/index.md)(val titleText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val descriptionText: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [Content](index.md) |

## Properties

| Name | Summary |
|---|---|
| [messageType](message-type.md) | [jvm]<br>val [messageType](message-type.md): [Content.MessageType](-message-type/index.md) |
