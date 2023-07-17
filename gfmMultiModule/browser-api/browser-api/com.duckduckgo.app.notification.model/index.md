//[browser-api](../../index.md)/[com.duckduckgo.app.notification.model](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Channel](-channel/index.md) | [androidJvm]<br>data class [Channel](-channel/index.md)(val id: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), @[StringRes](https://developer.android.com/reference/kotlin/androidx/annotation/StringRes.html)val name: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val priority: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [NotificationPlugin](-notification-plugin/index.md) | [androidJvm]<br>interface [NotificationPlugin](-notification-plugin/index.md) |
| [NotificationSpec](-notification-spec/index.md) | [androidJvm]<br>interface [NotificationSpec](-notification-spec/index.md) |
| [SchedulableNotification](-schedulable-notification/index.md) | [androidJvm]<br>interface [SchedulableNotification](-schedulable-notification/index.md)<br>This interface is used whenever we want to create a notification that can be scheduled if cancelIntent is null it then uses the default if &quot;com.duckduckgo.notification.cancel&quot; which will cancel the notification and send a pixel |
| [SchedulableNotificationPlugin](-schedulable-notification-plugin/index.md) | [androidJvm]<br>interface [SchedulableNotificationPlugin](-schedulable-notification-plugin/index.md) |
