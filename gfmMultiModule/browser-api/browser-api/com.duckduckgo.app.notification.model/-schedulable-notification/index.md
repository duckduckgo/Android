//[browser-api](../../../index.md)/[com.duckduckgo.app.notification.model](../index.md)/[SchedulableNotification](index.md)

# SchedulableNotification

[androidJvm]\
interface [SchedulableNotification](index.md)

This interface is used whenever we want to create a notification that can be scheduled if cancelIntent is null it then uses the default if &quot;com.duckduckgo.notification.cancel&quot; which will cancel the notification and send a pixel

## Properties

| Name | Summary |
|---|---|
| [id](id.md) | [androidJvm]<br>abstract val [id](id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [buildSpecification](build-specification.md) | [androidJvm]<br>abstract suspend fun [buildSpecification](build-specification.md)(): [NotificationSpec](../-notification-spec/index.md) |
| [canShow](can-show.md) | [androidJvm]<br>abstract suspend fun [canShow](can-show.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
