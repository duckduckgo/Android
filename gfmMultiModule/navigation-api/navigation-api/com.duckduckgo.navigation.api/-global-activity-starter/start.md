//[navigation-api](../../../index.md)/[com.duckduckgo.navigation.api](../index.md)/[GlobalActivityStarter](index.md)/[start](start.md)

# start

[androidJvm]\
abstract fun [start](start.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), params: [GlobalActivityStarter.ActivityParams](-activity-params/index.md), options: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)? = null)

Starts the activity given its [params](start.md).

The activity can later retrieve the [Serializable](start.md) using the extension function Bundle.getActivityParams

#### Parameters

androidJvm

| | |
|---|---|
| params | The activity parameters. They also identify the activity |
| context | the context used to start the activity |
| options | additional options for how the activity should be started, eg. scene transition animations |

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-illegal-argument-exception/index.html) | when the Activity can't be found |
