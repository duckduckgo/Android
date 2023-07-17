//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnEnabledNotificationContentPlugin](index.md)

# VpnEnabledNotificationContentPlugin

[androidJvm]\
interface [VpnEnabledNotificationContentPlugin](index.md)

## Types

| Name | Summary |
|---|---|
| [VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md) | [androidJvm]<br>data class [VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md)(val title: [SpannableStringBuilder](https://developer.android.com/reference/kotlin/android/text/SpannableStringBuilder.html), val message: [SpannableStringBuilder](https://developer.android.com/reference/kotlin/android/text/SpannableStringBuilder.html), val onNotificationPressIntent: [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)?, val notificationAction: [NotificationCompat.Action](https://developer.android.com/reference/kotlin/androidx/core/app/NotificationCompat.Action.html)?) |
| [VpnEnabledNotificationPriority](-vpn-enabled-notification-priority/index.md) | [androidJvm]<br>enum [VpnEnabledNotificationPriority](-vpn-enabled-notification-priority/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority](-vpn-enabled-notification-priority/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [getInitialContent](get-initial-content.md) | [androidJvm]<br>abstract fun [getInitialContent](get-initial-content.md)(): [VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md)?<br>This method will be called to show the first notification when the VPN is enabled. The method will be called from the main thread. |
| [getPriority](get-priority.md) | [androidJvm]<br>abstract fun [getPriority](get-priority.md)(): [VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority](-vpn-enabled-notification-priority/index.md)<br>The VPN will call this method to select what plugin will be displayed in the notification. To select a proper priority: |
| [getUpdatedContent](get-updated-content.md) | [androidJvm]<br>abstract fun [getUpdatedContent](get-updated-content.md)(): Flow&lt;[VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md)?&gt;<br>The VPN will subscribe to this flow, after it's being enabled, to get notification content updates. The method will NOT be called from the main thread. |
| [isActive](is-active.md) | [androidJvm]<br>abstract fun [isActive](is-active.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>The VPN will call this method to know whether this plugin is active or not. |
