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
 * Dialog which can be shown when user is required to select which saved credential to autofill
 */
interface GenerateSecurePasswordDialog {

    companion object {

        fun resultKey(tabId: String) = "${prefix(tabId, TAG)}/Result"

        const val TAG = "GenerateSecurePasswordDialog"
        const val KEY_URL = "url"
        const val KEY_PASSWORD = "password"
        const val KEY_ACCEPTED = "userAccepted"
        const val KEY_TAB_ID = "tabId"
    }
}

/**
 * Dialog which can be shown when user is required to select which saved credential to autofill
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
 */
interface CredentialSavePickerDialog {

    companion object {
        fun resultKeyUserChoseToSaveCredentials(tabId: String) = "${prefix(tabId, TAG)}/UserChoseToSave"
        fun resultKeyShouldPromptToDisableAutofill(tabId: String) = "${prefix(tabId, TAG)}/ShouldPromptToDisableAutofill"
        fun resultKeyPromptDismissed(tabId: String) = "${prefix(tabId, TAG)}/UserDismissedPrompt"

        const val TAG = "CredentialSavePickerDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_TAB_ID = "tabId"
    }
}

/**
 * Dialog which can be shown to prompt user to update existing saved credentials or not
 */
interface CredentialUpdateExistingCredentialsDialog {

    @Parcelize
    sealed interface CredentialUpdateType : Parcelable {

        @Parcelize
        object Username : CredentialUpdateType

        @Parcelize
        object Password : CredentialUpdateType
    }

    companion object {
        fun resultKeyCredentialUpdated(tabId: String) = "${prefix(tabId, TAG)}/UserChoseToUpdate"
        fun resultKeyPromptDismissed(tabId: String) = "${prefix(tabId, TAG)}/UserDismissedPrompt"

        const val TAG = "CredentialUpdateExistingCredentialsDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_TAB_ID = "tabId"
        const val KEY_CREDENTIAL_UPDATE_TYPE = "updateType"
    }
}

/**
 * Factory used to get instances of the various autofill dialogs
 */
interface CredentialAutofillDialogFactory {

    fun autofillSelectCredentialsDialog(
        url: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        tabId: String,
    ): DialogFragment

    fun autofillSavingCredentialsDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    fun autofillSavingUpdatePasswordDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    fun autofillSavingUpdateUsernameDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment

    fun autofillGeneratePasswordDialog(
        url: String,
        generatedPassword: String,
        tabId: String,
    ): DialogFragment
}

private fun prefix(
    tabId: String,
    tag: String,
): String {
    return "$tabId/$tag"
}
