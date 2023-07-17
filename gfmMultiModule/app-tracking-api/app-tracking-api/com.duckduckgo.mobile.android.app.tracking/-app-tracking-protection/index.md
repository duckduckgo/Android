//[app-tracking-api](../../../index.md)/[com.duckduckgo.mobile.android.app.tracking](../index.md)/[AppTrackingProtection](index.md)

# AppTrackingProtection

[androidJvm]\
interface [AppTrackingProtection](index.md)

## Functions

| Name | Summary |
|---|---|
| [isEnabled](is-enabled.md) | [androidJvm]<br>abstract suspend fun [isEnabled](is-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This is a suspend function because the operation can take time. You DO NOT need to set any dispatcher to call this suspend function |
| [isOnboarded](is-onboarded.md) | [androidJvm]<br>abstract fun [isOnboarded](is-onboarded.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method returns whether the user has gone through AppTP onboarding |
| [isRunning](is-running.md) | [androidJvm]<br>abstract suspend fun [isRunning](is-running.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This is a suspend function because the operation can take time You DO NOT need to set any dispatcher to call this suspend function |
| [restart](restart.md) | [androidJvm]<br>abstract fun [restart](restart.md)()<br>This method will restart the App Tracking Protection feature by disabling it and re-enabling back again |
