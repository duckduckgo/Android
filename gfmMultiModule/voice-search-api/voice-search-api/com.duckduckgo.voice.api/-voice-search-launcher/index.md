//[voice-search-api](../../../index.md)/[com.duckduckgo.voice.api](../index.md)/[VoiceSearchLauncher](index.md)

# VoiceSearchLauncher

[androidJvm]\
interface [VoiceSearchLauncher](index.md)

## Types

| Name | Summary |
|---|---|
| [Event](-event/index.md) | [androidJvm]<br>sealed class [Event](-event/index.md) |
| [Source](-source/index.md) | [androidJvm]<br>enum [Source](-source/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[VoiceSearchLauncher.Source](-source/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [launch](launch.md) | [androidJvm]<br>abstract fun [launch](launch.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [registerResultsCallback](register-results-callback.md) | [androidJvm]<br>abstract fun [registerResultsCallback](register-results-callback.md)(caller: [ActivityResultCaller](https://developer.android.com/reference/kotlin/androidx/activity/result/ActivityResultCaller.html), activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), source: [VoiceSearchLauncher.Source](-source/index.md), onEvent: ([VoiceSearchLauncher.Event](-event/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)) |
