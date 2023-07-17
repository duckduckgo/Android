//[remote-messaging-api](../../index.md)/[com.duckduckgo.remote.messaging.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Action](-action/index.md) | [jvm]<br>sealed class [Action](-action/index.md) |
| [AttributeMatcherPlugin](-attribute-matcher-plugin/index.md) | [jvm]<br>interface [AttributeMatcherPlugin](-attribute-matcher-plugin/index.md) |
| [Content](-content/index.md) | [jvm]<br>sealed class [Content](-content/index.md) |
| [MatchingAttribute](-matching-attribute/index.md) | [jvm]<br>interface [MatchingAttribute](-matching-attribute/index.md)&lt;[T](-matching-attribute/index.md)&gt; |
| [RemoteMessage](-remote-message/index.md) | [jvm]<br>data class [RemoteMessage](-remote-message/index.md)(val id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val content: [Content](-content/index.md), val matchingRules: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;, val exclusionRules: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)&gt;) |
| [RemoteMessagingRepository](-remote-messaging-repository/index.md) | [jvm]<br>interface [RemoteMessagingRepository](-remote-messaging-repository/index.md) |
