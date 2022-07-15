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

package com.duckduckgo.autofill

import androidx.fragment.app.DialogFragment
import com.duckduckgo.autofill.domain.app.LoginCredentials

/**
 * Dialog which can be shown when user is required to select which saved credential to autofill
 */
interface CredentialAutofillPickerDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialAutofillPickerDialog"
        const val RESULT_KEY_CREDENTIAL_PICKER = "CredentialAutofillPickerDialogResult"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
    }
}

/**
 * Dialog which can be shown to prompt user to save credentials or not
 */
interface CredentialSavePickerDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialSavePickerDialog"
        const val RESULT_KEY_CREDENTIAL_RESULT_SAVE = "CredentialSavePickerDialogResultSave"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
    }
}

/**
 * Dialog which can be shown to prompt user to update existing saved credentials or not
 */
interface CredentialUpdateExistingCredentialsDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialUpdateExistingCredentialsDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val RESULT_KEY_CREDENTIAL_RESULT_UPDATE = "CredentialUpdateExistingCredentialsResult"
    }
}

/**
 * A workaround caused by modularization:
 * clients using these dialogs will know them by their interface but will also need to know they are DialogFragments to show them
 */
interface DialogFragmentType {
    fun asDialogFragment(): DialogFragment
}

/**
 * Factory used to get instances of the various autofill dialogs
 */
interface CredentialAutofillDialogFactory {

    fun autofillSelectCredentialsDialog(url: String, credentials: List<LoginCredentials>): CredentialAutofillPickerDialog

    fun autofillSavingCredentialsDialog(url: String, credentials: LoginCredentials): CredentialSavePickerDialog

    fun autofillSavingUpdateCredentialsDialog(url: String, credentials: LoginCredentials): CredentialUpdateExistingCredentialsDialog

}
