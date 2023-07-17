//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[InternalTestUserChecker](index.md)/[verifyVerificationErrorReceived](verify-verification-error-received.md)

# verifyVerificationErrorReceived

[androidJvm]\
abstract fun [verifyVerificationErrorReceived](verify-verification-error-received.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

This method should be called if an error is received when loading a [url](verify-verification-error-received.md). This will only be processed if the [url](verify-verification-error-received.md) passed is a valid internal tester success verification url else it will just be ignored.
