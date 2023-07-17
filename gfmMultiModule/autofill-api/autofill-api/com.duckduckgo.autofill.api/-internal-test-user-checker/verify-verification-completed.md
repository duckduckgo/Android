//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[InternalTestUserChecker](index.md)/[verifyVerificationCompleted](verify-verification-completed.md)

# verifyVerificationCompleted

[androidJvm]\
abstract fun [verifyVerificationCompleted](verify-verification-completed.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

This method should be called if the [url](verify-verification-completed.md) is completely loaded. This will only be processed if the [url](verify-verification-completed.md) passed is a valid internal tester success verification url else it will just be ignored.
