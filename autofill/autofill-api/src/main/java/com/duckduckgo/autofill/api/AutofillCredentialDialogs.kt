/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.api

import android.os.Parcelable
import androidx.fragment.app.DialogFragment
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import kotlinx.parcelize.Parcelize

/**
 * Dialog which can be shown when user is required to select whether to use generated password or not
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface UseGeneratedPasswordDialog {

    companion object {

        fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

        const val TAG = "GenerateSecurePasswordDialog"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_ACCEPTED = "userAccepted"
        const val KEY_TAB_ID = "tabId"
    }
}

/**
 * Dialog which can be shown when user is required to select which saved credential to autofill
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface CredentialAutofillPickerDialog {

    companion object {

        fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

        const val TAG = "CredentialAutofillPickerDialog"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_TRIGGER_TYPE = "triggerType"
        const val KEY_TAB_ID = "tabId"
    }
}

/**
 * Dialog which can be shown to prompt user to save credentials or not
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface CredentialSavePickerDialog {

    companion object {
        fun resultKeyUserChoseToSaveCredentials(tabId: String) = "${prefix(tabId, TAG)}/UserChoseToSave"
        fun resultKeyShouldPromptToDisableAutofill(tabId: String) =
            "${prefix(tabId, TAG)}/ShouldPromptToDisableAutofill"

        const val TAG = "CredentialSavePickerDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_TAB_ID = "tabId"
    }
}

/**
 * Dialog which can be shown to prompt user to update existing saved credentials or not
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface CredentialUpdateExistingCredentialsDialog {

    @Parcelize
    sealed interface CredentialUpdateType : Parcelable {

        @Parcelize
        data object Username : CredentialUpdateType

        @Parcelize
        data object Password : CredentialUpdateType
    }

    companion object {
        fun resultKeyCredentialUpdated(tabId: String) = "${prefix(tabId, TAG)}/UserChoseToUpdate"

        const val TAG = "CredentialUpdateExistingCredentialsDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_TAB_ID = "tabId"
        const val KEY_CREDENTIAL_UPDATE_TYPE = "updateType"
    }
}

/**
 * Dialog which prompts the user to choose whether to use their personal duck address or a private alias address
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface EmailProtectionChooseEmailDialog {

    /**
     * Result of the dialog, as determined by which button the user pressed or if they cancelled the dialog
     */
    @Parcelize
    sealed interface UseEmailResultType : Parcelable {

        /**
         * User chose to use their personal duck address
         */
        @Parcelize
        object UsePersonalEmailAddress : UseEmailResultType

        /**
         * User chose to use a private alias address
         */
        @Parcelize
        object UsePrivateAliasAddress : UseEmailResultType

        /**
         * User cancelled the dialog
         */
        @Parcelize
        object DoNotUseEmailProtection : UseEmailResultType
    }

    companion object {
        fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

        const val TAG = "EmailProtectionChooserDialog"
        const val KEY_URL = "url"
        const val KEY_RESULT = "result"
    }
}

/**
 * Dialog which prompts the user to enable Email Protection
 * Results should be handled by defining a [AutofillFragmentResultsPlugin]
 */
interface EmailProtectionInContextSignUpDialog {

    /**
     * Result of the dialog, as determined by which button the user pressed or if they cancelled the dialog
     */
    @Parcelize
    sealed interface EmailProtectionInContextSignUpResult : Parcelable {

        /**
         * User chose to enable Email Protection
         */
        @Parcelize
        object SignUp : EmailProtectionInContextSignUpResult

        /**
         * User chose to dismiss dialog
         */
        @Parcelize
        object Cancel : EmailProtectionInContextSignUpResult

        /**
         * User chose to dismiss dialog and not be shown again
         */
        @Parcelize
        object DoNotShowAgain : EmailProtectionInContextSignUpResult
    }

    companion object {
        fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

        const val TAG = "EmailProtectionInContextSignUpDialog"
        const val KEY_RESULT = "result"
    }
}

/**
 * Factory used to get instances of the various autofill dialogs
 */
interface CredentialAutofillDialogFactory {

    /**
     * Creates a dialog which prompts the user to choose which saved credential to autofill
     */
    fun autofillSelectCredentialsDialog(
        url: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to choose whether to save credentials or not
     */
    fun autofillSavingCredentialsDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to choose whether to update an existing credential's password
     */
    fun autofillSavingUpdatePasswordDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to choose whether to update an existing credential's username
     */
    fun autofillSavingUpdateUsernameDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to choose whether to use generated password or not
     */
    fun autofillGeneratePasswordDialog(
        url: String,
        username: String?,
        generatedPassword: String,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to choose whether to use their personal duck address or a private alias address
     */
    fun autofillEmailProtectionEmailChooserDialog(
        url: String,
        personalDuckAddress: String,
        tabId: String,
    ): DialogFragment

    /**
     * Creates a dialog which prompts the user to sign up for Email Protection
     */
    fun emailProtectionInContextSignUpDialog(tabId: String): DialogFragment

    /**
     * Creates a dialog which prompts the user to import passwords from Google Passwords
     */
    fun autofillImportPasswordsPromoDialog(importSource: AutofillImportLaunchSource, tabId: String, url: String): DialogFragment
}

private fun prefix(
    tabId: String,
    tag: String,
): String {
    return "$tabId/$tag"
}

@Parcelize
enum class AutofillImportLaunchSource(val value: String) : Parcelable {
    PasswordManagementPromo("password_management_promo"),
    PasswordManagementEmptyState("password_management_empty_state"),
    PasswordManagementOverflow("password_management_overflow"),
    AutofillSettings("autofill_settings_button"),
    InBrowserPromo("in_browser_promo"),
    Unknown("unknown"),
}
