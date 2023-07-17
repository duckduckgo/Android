//[navigation-api](../../../index.md)/[com.duckduckgo.navigation.api](../index.md)/[GlobalActivityStarter](index.md)

# GlobalActivityStarter

[androidJvm]\
interface [GlobalActivityStarter](index.md)

This is the Activity Starter. Use this type to start Activities or get their start intent.

Activities to launch are identified by their input [ActivityParams](-activity-params/index.md).

```kotlin
data class ExampleActivityParams(...) : ActivityParams

globalActivityStarter.start(context, ExampleActivityParams(...))
```

## Types

| Name | Summary |
|---|---|
| [ActivityParams](-activity-params/index.md) | [androidJvm]<br>interface [ActivityParams](-activity-params/index.md) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)<br>This is a marker class Mark all data classes related to activity arguments with this type |
| [ParamToActivityMapper](-param-to-activity-mapper/index.md) | [androidJvm]<br>interface [ParamToActivityMapper](-param-to-activity-mapper/index.md)<br>Implement this mapper that will return [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html) class for the given parameters. Once implemented it, you need to contribute it as a multibinding using ContributesMultibinding into the AppScope. |

## Functions

| Name | Summary |
|---|---|
| [start](start.md) | [androidJvm]<br>abstract fun [start](start.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), params: [GlobalActivityStarter.ActivityParams](-activity-params/index.md), options: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)? = null)<br>Starts the activity given its [params](start.md). |
| [startIntent](start-intent.md) | [androidJvm]<br>abstract fun [startIntent](start-intent.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), params: [GlobalActivityStarter.ActivityParams](-activity-params/index.md)): [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)?<br>Returns  the [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html) that can be used to start the [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), given the [ActivityParams](-activity-params/index.md). This method will generally be used to start the activity for result. |
