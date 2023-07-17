//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[Callback](index.md)/[onGeneratedPasswordAvailableToUse](on-generated-password-available-to-use.md)

# onGeneratedPasswordAvailableToUse

[androidJvm]\
abstract suspend fun [onGeneratedPasswordAvailableToUse](on-generated-password-available-to-use.md)(originalUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, generatedPassword: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Called when we've generated a password for the user, and we want to offer it to them to use. When this is called, we should present the generated password to the user for them to choose whether to use it or not.
