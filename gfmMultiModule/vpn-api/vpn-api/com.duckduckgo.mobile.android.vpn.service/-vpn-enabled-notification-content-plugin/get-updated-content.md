//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnEnabledNotificationContentPlugin](index.md)/[getUpdatedContent](get-updated-content.md)

# getUpdatedContent

[androidJvm]\
abstract fun [getUpdatedContent](get-updated-content.md)(): Flow&lt;[VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md)?&gt;

The VPN will subscribe to this flow, after it's being enabled, to get notification content updates. The method will NOT be called from the main thread.

#### Return

shall return a flow of notification content updates, or null if the plugin does not want to show content in the notification.
