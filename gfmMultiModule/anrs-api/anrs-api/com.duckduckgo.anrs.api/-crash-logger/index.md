//[anrs-api](../../../index.md)/[com.duckduckgo.anrs.api](../index.md)/[CrashLogger](index.md)

# CrashLogger

[androidJvm]\
interface [CrashLogger](index.md)

## Types

| Name | Summary |
|---|---|
| [Crash](-crash/index.md) | [androidJvm]<br>data class [Crash](-crash/index.md)(val shortName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val t: [Throwable](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-throwable/index.html)) |

## Functions

| Name | Summary |
|---|---|
| [logCrash](log-crash.md) | [androidJvm]<br>@[WorkerThread](https://developer.android.com/reference/kotlin/androidx/annotation/WorkerThread.html)<br>abstract fun [logCrash](log-crash.md)(crash: [CrashLogger.Crash](-crash/index.md))<br>Logs the [Crash](-crash/index.md) to be sent later on to the backend |
