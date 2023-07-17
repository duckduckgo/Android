//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api.ui.credential.saving.declines](../index.md)/[AutofillDeclineCounter](index.md)

# AutofillDeclineCounter

[androidJvm]\
interface [AutofillDeclineCounter](index.md)

Repeated prompts to use Autofill (e.g., save login credentials) might annoy a user who doesn't want to use Autofill. If the user has declined too many times without using it, we will prompt them to disable.

This class is used to track the number of times a user has declined to use Autofill when prompted. It should be permanently disabled, by calling disableDeclineCounter(), when user:     - saves a credential, or     - chooses to disable autofill when prompted to disable autofill, or     - chooses to keep using autofill when prompted to disable autofill

## Functions

| Name | Summary |
|---|---|
| [disableDeclineCounter](disable-decline-counter.md) | [androidJvm]<br>abstract suspend fun [disableDeclineCounter](disable-decline-counter.md)()<br>Permanently disable the autofill decline counter |
| [shouldPromptToDisableAutofill](should-prompt-to-disable-autofill.md) | [androidJvm]<br>abstract suspend fun [shouldPromptToDisableAutofill](should-prompt-to-disable-autofill.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Determine if the user should be prompted to disable autofill |
| [userDeclinedToSaveCredentials](user-declined-to-save-credentials.md) | [androidJvm]<br>abstract suspend fun [userDeclinedToSaveCredentials](user-declined-to-save-credentials.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)<br>Should be called every time a user declines to save credentials |
