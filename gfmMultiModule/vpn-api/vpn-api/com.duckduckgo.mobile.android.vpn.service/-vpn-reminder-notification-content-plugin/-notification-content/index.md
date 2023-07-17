//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../../index.md)/[VpnReminderNotificationContentPlugin](../index.md)/[NotificationContent](index.md)

# NotificationContent

[androidJvm]\
data class [NotificationContent](index.md)(val isSilent: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), val shouldAutoCancel: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?, @[LayoutRes](https://developer.android.com/reference/kotlin/androidx/annotation/LayoutRes.html)val customViewLayout: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val onNotificationPressIntent: [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)?, val notificationAction: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NotificationCompat.Action](https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Action.html)&gt;)

## Constructors

| | |
|---|---|
| [NotificationContent](-notification-content.md) | [androidJvm]<br>constructor(isSilent: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), shouldAutoCancel: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?, @[LayoutRes](https://developer.android.com/reference/kotlin/androidx/annotation/LayoutRes.html)customViewLayout: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), onNotificationPressIntent: [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)?, notificationAction: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NotificationCompat.Action](https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Action.html)&gt;) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [androidJvm]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [customViewLayout](custom-view-layout.md) | [androidJvm]<br>val [customViewLayout](custom-view-layout.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [isSilent](is-silent.md) | [androidJvm]<br>val [isSilent](is-silent.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [notificationAction](notification-action.md) | [androidJvm]<br>val [notificationAction](notification-action.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NotificationCompat.Action](https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Action.html)&gt; |
| [onNotificationPressIntent](on-notification-press-intent.md) | [androidJvm]<br>val [onNotificationPressIntent](on-notification-press-intent.md): [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)? |
| [shouldAutoCancel](should-auto-cancel.md) | [androidJvm]<br>val [shouldAutoCancel](should-auto-cancel.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)? |
