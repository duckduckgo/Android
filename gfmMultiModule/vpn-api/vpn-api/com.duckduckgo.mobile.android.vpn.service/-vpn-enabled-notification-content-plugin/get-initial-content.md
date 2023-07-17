//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnEnabledNotificationContentPlugin](index.md)/[getInitialContent](get-initial-content.md)

# getInitialContent

[androidJvm]\
abstract fun [getInitialContent](get-initial-content.md)(): [VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent](-vpn-enabled-notification-content/index.md)?

This method will be called to show the first notification when the VPN is enabled. The method will be called from the main thread.

#### Return

shall return the content of the notification or null if the plugin does not want to show content in the notification.
