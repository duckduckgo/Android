//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[InternalTestUserChecker](index.md)

# InternalTestUserChecker

[androidJvm]\
interface [InternalTestUserChecker](index.md)

Public API that could be used if the user is a verified internal tester. This class could potentially be moved to a different module once there's a need for it in the future.

## Properties

| Name | Summary |
|---|---|
| [isInternalTestUser](is-internal-test-user.md) | [androidJvm]<br>abstract val [isInternalTestUser](is-internal-test-user.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This checks if the user has went through the process of becoming a verified test user |

## Functions

| Name | Summary |
|---|---|
| [verifyVerificationCompleted](verify-verification-completed.md) | [androidJvm]<br>abstract fun [verifyVerificationCompleted](verify-verification-completed.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>This method should be called if the [url](verify-verification-completed.md) is completely loaded. This will only be processed if the [url](verify-verification-completed.md) passed is a valid internal tester success verification url else it will just be ignored. |
| [verifyVerificationErrorReceived](verify-verification-error-received.md) | [androidJvm]<br>abstract fun [verifyVerificationErrorReceived](verify-verification-error-received.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>This method should be called if an error is received when loading a [url](verify-verification-error-received.md). This will only be processed if the [url](verify-verification-error-received.md) passed is a valid internal tester success verification url else it will just be ignored. |
