//[browser-api](../../../index.md)/[com.duckduckgo.app.notification.model](../index.md)/[SchedulableNotificationPlugin](index.md)

# SchedulableNotificationPlugin

[androidJvm]\
interface [SchedulableNotificationPlugin](index.md)

## Functions

| Name | Summary |
|---|---|
| [getLaunchIntent](get-launch-intent.md) | [androidJvm]<br>abstract fun [getLaunchIntent](get-launch-intent.md)(): [PendingIntent](https://developer.android.com/reference/kotlin/android/app/PendingIntent.html)? |
| [getSchedulableNotification](get-schedulable-notification.md) | [androidJvm]<br>abstract fun [getSchedulableNotification](get-schedulable-notification.md)(): [SchedulableNotification](../-schedulable-notification/index.md) |
| [getSpecification](get-specification.md) | [androidJvm]<br>abstract fun [getSpecification](get-specification.md)(): [NotificationSpec](../-notification-spec/index.md) |
| [onNotificationCancelled](on-notification-cancelled.md) | [androidJvm]<br>abstract fun [onNotificationCancelled](on-notification-cancelled.md)() |
| [onNotificationShown](on-notification-shown.md) | [androidJvm]<br>abstract fun [onNotificationShown](on-notification-shown.md)() |
