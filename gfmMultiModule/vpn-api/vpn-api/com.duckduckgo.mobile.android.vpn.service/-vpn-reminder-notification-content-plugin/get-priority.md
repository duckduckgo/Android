//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnReminderNotificationContentPlugin](index.md)/[getPriority](get-priority.md)

# getPriority

[androidJvm]\
abstract fun [getPriority](get-priority.md)(): [VpnReminderNotificationContentPlugin.NotificationPriority](-notification-priority/index.md)

The VPN will call this method to select what plugin will be displayed in the notification. To select a proper priority:

- 
   check the priority of any other plugins
- 
   check with product/design what should be the priority of your plugin w.r.t. other plugins

#### Return

shall return the priority of the plugin.
