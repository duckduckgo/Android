//[device-auth-api](../../../index.md)/[com.duckduckgo.deviceauth.api](../index.md)/[DeviceAuthenticator](index.md)

# DeviceAuthenticator

[androidJvm]\
interface [DeviceAuthenticator](index.md)

## Types

| Name | Summary |
|---|---|
| [AuthResult](-auth-result/index.md) | [androidJvm]<br>sealed class [AuthResult](-auth-result/index.md) |
| [Features](-features/index.md) | [androidJvm]<br>enum [Features](-features/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[DeviceAuthenticator.Features](-features/index.md)&gt; |

## Functions

| Name | Summary |
|---|---|
| [authenticate](authenticate.md) | [androidJvm]<br>@[UiThread](https://developer.android.com/reference/kotlin/androidx/annotation/UiThread.html)<br>abstract fun [authenticate](authenticate.md)(featureToAuth: [DeviceAuthenticator.Features](-features/index.md), fragment: [Fragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/Fragment.html), onResult: ([DeviceAuthenticator.AuthResult](-auth-result/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))<br>Launches a device authentication flow for a specific [featureToAuth](authenticate.md) from a [fragment](authenticate.md). [onResult](authenticate.md) can be used to communicate back to the feature the result of the flow.<br>[androidJvm]<br>@[UiThread](https://developer.android.com/reference/kotlin/androidx/annotation/UiThread.html)<br>abstract fun [authenticate](authenticate.md)(featureToAuth: [DeviceAuthenticator.Features](-features/index.md), fragmentActivity: [FragmentActivity](https://developer.android.com/reference/kotlin/androidx/fragment/app/FragmentActivity.html), onResult: ([DeviceAuthenticator.AuthResult](-auth-result/index.md)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))<br>Launches a device authentication flow for a specific [featureToAuth](authenticate.md) from a [fragmentActivity](authenticate.md). [onResult](authenticate.md) can be used to communicate back to the feature the result of the flow. |
| [hasValidDeviceAuthentication](has-valid-device-authentication.md) | [androidJvm]<br>abstract fun [hasValidDeviceAuthentication](has-valid-device-authentication.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method can be used to check if the user's device has a valid device authentication enrolled (Fingerprint, PIN, pattern or password). |
