//[device-auth-api](../../../index.md)/[com.duckduckgo.deviceauth.api](../index.md)/[AutofillAuthorizationGracePeriod](index.md)

# AutofillAuthorizationGracePeriod

[androidJvm]\
interface [AutofillAuthorizationGracePeriod](index.md)

A grace period for autofill authorization. This is used to allow autofill authorization to be skipped for a short period of time after a successful authorization.

## Functions

| Name | Summary |
|---|---|
| [invalidate](invalidate.md) | [androidJvm]<br>abstract fun [invalidate](invalidate.md)()<br>Invalidates the grace period, so that the next call to [isAuthRequired](is-auth-required.md) will return true |
| [isAuthRequired](is-auth-required.md) | [androidJvm]<br>abstract fun [isAuthRequired](is-auth-required.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Can be used to determine if device auth is required. If not required, it can be bypassed. |
| [recordSuccessfulAuthorization](record-successful-authorization.md) | [androidJvm]<br>abstract fun [recordSuccessfulAuthorization](record-successful-authorization.md)()<br>Records the timestamp of a successful device authorization |
