//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnReminderNotificationContentPlugin](index.md)

# VpnReminderNotificationContentPlugin

[androidJvm]\
interface [VpnReminderNotificationContentPlugin](index.md)

## Types

| Name | Summary |
|---|---|
| [NotificationContent](-notification-content/index.md) | [androidJvm]<br>data class [NotificationContent](-notification-content/index.md)(val isSilent: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), val shouldAutoCancel: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?, @[LayoutRes](https://developer.android.com/reference/kotlin/androidx/annotation/LayoutRes.html)val customViewLayout: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val onNotificationPressIntent: [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)?, val notificationAction: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[NotificationCompat.Action](https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Action.html)&gt;) |
| [NotificationPriority](-notification-priority/index.md) | [androidJvm]<br>enum [NotificationPriority](-notification-priority/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnReminderNotificationContentPlugin.NotificationPriority](-notification-priority/index.md)&gt; |
| [Type](-type/index.md) | [androidJvm]<br>enum [Type](-type/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnReminderNotificationContentPlugin.Type](-type/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [getContent](get-content.md) | [androidJvm]<br>abstract fun [getContent](get-content.md)(): [VpnReminderNotificationContentPlugin.NotificationContent](-notification-content/index.md)?<br>This method could be called to get the plugin's corresponding NotificationContent |
| [getPriority](get-priority.md) | [androidJvm]<br>abstract fun [getPriority](get-priority.md)(): [VpnReminderNotificationContentPlugin.NotificationPriority](-notification-priority/index.md)<br>The VPN will call this method to select what plugin will be displayed in the notification. To select a proper priority: |
| [getType](get-type.md) | [androidJvm]<br>abstract fun [getType](get-type.md)(): [VpnReminderNotificationContentPlugin.Type](-type/index.md)<br>This method will be called to select the specific Type of reminder notification that vpn wants to show. |
