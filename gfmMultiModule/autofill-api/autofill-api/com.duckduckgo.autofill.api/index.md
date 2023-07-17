//[autofill-api](../../index.md)/[com.duckduckgo.autofill.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Autofill](-autofill/index.md) | [androidJvm]<br>interface [Autofill](-autofill/index.md)<br>Public interface for the Autofill feature |
| [AutofillCapabilityChecker](-autofill-capability-checker/index.md) | [androidJvm]<br>interface [AutofillCapabilityChecker](-autofill-capability-checker/index.md)<br>Used to check the status of various Autofill features. |
| [AutofillException](-autofill-exception/index.md) | [androidJvm]<br>data class [AutofillException](-autofill-exception/index.md)(val domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val reason: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Public data class for Autofill Exceptions |
| [AutofillFeature](-autofill-feature/index.md) | [androidJvm]<br>interface [AutofillFeature](-autofill-feature/index.md)<br>This is the class that represents the autofill feature flags |
| [AutofillSettingsActivityLauncher](-autofill-settings-activity-launcher/index.md) | [androidJvm]<br>interface [AutofillSettingsActivityLauncher](-autofill-settings-activity-launcher/index.md)<br>Used to access an Intent which will launch the autofill settings activity The activity is implemented in the impl module and is otherwise inaccessible from outside this module. |
| [BrowserAutofill](-browser-autofill/index.md) | [androidJvm]<br>interface [BrowserAutofill](-browser-autofill/index.md)<br>Public interface for accessing and configuring browser autofill functionality for a WebView instance |
| [Callback](-callback/index.md) | [androidJvm]<br>interface [Callback](-callback/index.md)<br>Browser Autofill callbacks |
| [CredentialAutofillDialogFactory](-credential-autofill-dialog-factory/index.md) | [androidJvm]<br>interface [CredentialAutofillDialogFactory](-credential-autofill-dialog-factory/index.md)<br>Factory used to get instances of the various autofill dialogs |
| [CredentialAutofillPickerDialog](-credential-autofill-picker-dialog/index.md) | [androidJvm]<br>interface [CredentialAutofillPickerDialog](-credential-autofill-picker-dialog/index.md)<br>Dialog which can be shown when user is required to select which saved credential to autofill |
| [CredentialSavePickerDialog](-credential-save-picker-dialog/index.md) | [androidJvm]<br>interface [CredentialSavePickerDialog](-credential-save-picker-dialog/index.md)<br>Dialog which can be shown to prompt user to save credentials or not |
| [CredentialUpdateExistingCredentialsDialog](-credential-update-existing-credentials-dialog/index.md) | [androidJvm]<br>interface [CredentialUpdateExistingCredentialsDialog](-credential-update-existing-credentials-dialog/index.md)<br>Dialog which can be shown to prompt user to update existing saved credentials or not |
| [ExistingCredentialMatchDetector](-existing-credential-match-detector/index.md) | [androidJvm]<br>interface [ExistingCredentialMatchDetector](-existing-credential-match-detector/index.md)<br>Used to determine if the given credential details exist in the autofill storage |
| [InternalTestUserChecker](-internal-test-user-checker/index.md) | [androidJvm]<br>interface [InternalTestUserChecker](-internal-test-user-checker/index.md)<br>Public API that could be used if the user is a verified internal tester. This class could potentially be moved to a different module once there's a need for it in the future. |
| [UseGeneratedPasswordDialog](-use-generated-password-dialog/index.md) | [androidJvm]<br>interface [UseGeneratedPasswordDialog](-use-generated-password-dialog/index.md)<br>Dialog which can be shown when user is required to select whether to use generated password or not |
