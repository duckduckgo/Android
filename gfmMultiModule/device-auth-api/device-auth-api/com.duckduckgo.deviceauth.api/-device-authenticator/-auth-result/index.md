//[device-auth-api](../../../../index.md)/[com.duckduckgo.deviceauth.api](../../index.md)/[DeviceAuthenticator](../index.md)/[AuthResult](index.md)

# AuthResult

sealed class [AuthResult](index.md)

#### Inheritors

| |
|---|
| [Success](-success/index.md) |
| [UserCancelled](-user-cancelled/index.md) |
| [Error](-error/index.md) |

## Types

| Name | Summary |
|---|---|
| [Error](-error/index.md) | [androidJvm]<br>data class [Error](-error/index.md)(val reason: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [DeviceAuthenticator.AuthResult](index.md) |
| [Success](-success/index.md) | [androidJvm]<br>object [Success](-success/index.md) : [DeviceAuthenticator.AuthResult](index.md) |
| [UserCancelled](-user-cancelled/index.md) | [androidJvm]<br>object [UserCancelled](-user-cancelled/index.md) : [DeviceAuthenticator.AuthResult](index.md) |
