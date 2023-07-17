//[anrs-api](../../../index.md)/[com.duckduckgo.anrs.api](../index.md)/[CrashLogger](index.md)/[logCrash](log-crash.md)

# logCrash

[androidJvm]\

@[WorkerThread](https://developer.android.com/reference/kotlin/androidx/annotation/WorkerThread.html)

abstract fun [logCrash](log-crash.md)(crash: [CrashLogger.Crash](-crash/index.md))

Logs the [Crash](-crash/index.md) to be sent later on to the backend

This method shall be executed off the main thread otherwise it will throw [IllegalStateException](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-illegal-state-exception/index.html)

#### Parameters

androidJvm

| | |
|---|---|
| crash | [Crash](-crash/index.md) model |
