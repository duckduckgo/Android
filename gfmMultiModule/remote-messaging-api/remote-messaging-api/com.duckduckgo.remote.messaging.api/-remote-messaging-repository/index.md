//[remote-messaging-api](../../../index.md)/[com.duckduckgo.remote.messaging.api](../index.md)/[RemoteMessagingRepository](index.md)

# RemoteMessagingRepository

[jvm]\
interface [RemoteMessagingRepository](index.md)

## Functions

| Name | Summary |
|---|---|
| [activeMessage](active-message.md) | [jvm]<br>abstract fun [activeMessage](active-message.md)(message: [RemoteMessage](../-remote-message/index.md)?) |
| [didShow](did-show.md) | [jvm]<br>abstract fun [didShow](did-show.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [dismissedMessages](dismissed-messages.md) | [jvm]<br>abstract fun [dismissedMessages](dismissed-messages.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |
| [dismissMessage](dismiss-message.md) | [jvm]<br>abstract suspend fun [dismissMessage](dismiss-message.md)(id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [markAsShown](mark-as-shown.md) | [jvm]<br>abstract fun [markAsShown](mark-as-shown.md)(remoteMessage: [RemoteMessage](../-remote-message/index.md)) |
| [messageFlow](message-flow.md) | [jvm]<br>abstract fun [messageFlow](message-flow.md)(): Flow&lt;[RemoteMessage](../-remote-message/index.md)?&gt; |
