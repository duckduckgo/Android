//[device-auth-api](../../../index.md)/[com.duckduckgo.deviceauth.api](../index.md)/[DeviceAuthenticator](index.md)/[authenticate](authenticate.md)

# authenticate

[androidJvm]\

@[UiThread](https://developer.android.com/reference/kotlin/androidx/annotation/UiThread.html)

abstract fun [authenticate](authenticate.md)(featureToAuth: [DeviceAuthenticator.Features](-features/index.md), fragment: [Fragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/Fragment.html), onResult: ([DeviceAuthenticator.AuthResult](-auth-result/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))

Launches a device authentication flow for a specific [featureToAuth](authenticate.md) from a [fragment](authenticate.md). [onResult](authenticate.md) can be used to communicate back to the feature the result of the flow.

[androidJvm]\

@[UiThread](https://developer.android.com/reference/kotlin/androidx/annotation/UiThread.html)

abstract fun [authenticate](authenticate.md)(featureToAuth: [DeviceAuthenticator.Features](-features/index.md), fragmentActivity: [FragmentActivity](https://developer.android.com/reference/kotlin/androidx/fragment/app/FragmentActivity.html), onResult: ([DeviceAuthenticator.AuthResult](-auth-result/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))

Launches a device authentication flow for a specific [featureToAuth](authenticate.md) from a [fragmentActivity](authenticate.md). [onResult](authenticate.md) can be used to communicate back to the feature the result of the flow.
