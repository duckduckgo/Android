//[app-tracking-api](../../../index.md)/[com.duckduckgo.mobile.android.app.tracking](../index.md)/[AppTrackerDetector](index.md)/[evaluate](evaluate.md)

# evaluate

[androidJvm]\

@[WorkerThread](https://developer.android.com/reference/kotlin/androidx/annotation/WorkerThread.html)

abstract fun [evaluate](evaluate.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), uid: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [AppTrackerDetector.AppTracker](-app-tracker/index.md)?

Evaluates whether the specified domain requested by the specified uid is a tracker. This method should be called off the UI thread.

#### Return

[AppTracker](-app-tracker/index.md) if the request is a tracker, null otherwise

#### Parameters

androidJvm

| | |
|---|---|
| domain | the domain to evaluate |
| uid | the uid of the app requesting the domain |
