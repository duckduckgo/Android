//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[CredentialAutofillDialogFactory](index.md)

# CredentialAutofillDialogFactory

[androidJvm]\
interface [CredentialAutofillDialogFactory](index.md)

Factory used to get instances of the various autofill dialogs

## Functions

| Name | Summary |
|---|---|
| [autofillGeneratePasswordDialog](autofill-generate-password-dialog.md) | [androidJvm]<br>abstract fun [autofillGeneratePasswordDialog](autofill-generate-password-dialog.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, generatedPassword: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [DialogFragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/DialogFragment.html) |
| [autofillSavingCredentialsDialog](autofill-saving-credentials-dialog.md) | [androidJvm]<br>abstract fun [autofillSavingCredentialsDialog](autofill-saving-credentials-dialog.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [DialogFragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/DialogFragment.html) |
| [autofillSavingUpdatePasswordDialog](autofill-saving-update-password-dialog.md) | [androidJvm]<br>abstract fun [autofillSavingUpdatePasswordDialog](autofill-saving-update-password-dialog.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [DialogFragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/DialogFragment.html) |
| [autofillSavingUpdateUsernameDialog](autofill-saving-update-username-dialog.md) | [androidJvm]<br>abstract fun [autofillSavingUpdateUsernameDialog](autofill-saving-update-username-dialog.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [DialogFragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/DialogFragment.html) |
| [autofillSelectCredentialsDialog](autofill-select-credentials-dialog.md) | [androidJvm]<br>abstract fun [autofillSelectCredentialsDialog](autofill-select-credentials-dialog.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), credentials: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)&gt;, triggerType: [LoginTriggerType](../../com.duckduckgo.autofill.api.domain.app/-login-trigger-type/index.md), tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [DialogFragment](https://developer.android.com/reference/kotlin/androidx/fragment/app/DialogFragment.html) |
