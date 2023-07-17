//[app-tracking-api](../../../index.md)/[com.duckduckgo.mobile.android.app.tracking](../index.md)/[AppTrackerDetector](index.md)

# AppTrackerDetector

[androidJvm]\
interface [AppTrackerDetector](index.md)

## Types

| Name | Summary |
|---|---|
| [AppTracker](-app-tracker/index.md) | [androidJvm]<br>data class [AppTracker](-app-tracker/index.md)(val domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val uid: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val trackerCompanyDisplayName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val trackingAppId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val trackingAppName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |

## Functions

| Name | Summary |
|---|---|
| [evaluate](evaluate.md) | [androidJvm]<br>@[WorkerThread](https://developer.android.com/reference/kotlin/androidx/annotation/WorkerThread.html)<br>abstract fun [evaluate](evaluate.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), uid: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [AppTrackerDetector.AppTracker](-app-tracker/index.md)?<br>Evaluates whether the specified domain requested by the specified uid is a tracker. This method should be called off the UI thread. |
