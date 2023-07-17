//[remote-messaging-api](../../../index.md)/[com.duckduckgo.remote.messaging.api](../index.md)/[Action](index.md)

# Action

sealed class [Action](index.md)

#### Inheritors

| |
|---|
| [Url](-url/index.md) |
| [PlayStore](-play-store/index.md) |
| [DefaultBrowser](-default-browser/index.md) |
| [Dismiss](-dismiss/index.md) |
| [AppTpOnboarding](-app-tp-onboarding/index.md) |

## Types

| Name | Summary |
|---|---|
| [ActionType](-action-type/index.md) | [jvm]<br>enum [ActionType](-action-type/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[Action.ActionType](-action-type/index.md)&gt; |
| [AppTpOnboarding](-app-tp-onboarding/index.md) | [jvm]<br>data class [AppTpOnboarding](-app-tp-onboarding/index.md)(val value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = &quot;&quot;) : [Action](index.md) |
| [DefaultBrowser](-default-browser/index.md) | [jvm]<br>data class [DefaultBrowser](-default-browser/index.md)(val value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = &quot;&quot;) : [Action](index.md) |
| [Dismiss](-dismiss/index.md) | [jvm]<br>data class [Dismiss](-dismiss/index.md)(val value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) = &quot;&quot;) : [Action](index.md) |
| [PlayStore](-play-store/index.md) | [jvm]<br>data class [PlayStore](-play-store/index.md)(val value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [Action](index.md) |
| [Url](-url/index.md) | [jvm]<br>data class [Url](-url/index.md)(val value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [Action](index.md) |

## Properties

| Name | Summary |
|---|---|
| [actionType](action-type.md) | [jvm]<br>val [actionType](action-type.md): [Action.ActionType](-action-type/index.md) |
