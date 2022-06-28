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

interface CredentialAutofillPickerDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialAutofillPickerDialog"
        const val RESULT_KEY_CREDENTIAL_PICKER = "CredentialAutofillPickerDialogResult"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
    }
}

interface CredentialSavePickerDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialSavePickerDialog"
        const val RESULT_KEY_CREDENTIAL_RESULT_SAVE = "CredentialSavePickerDialogResultSave"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
    }
}

interface CredentialUpdateExistingCredentialsDialog : DialogFragmentType {

    companion object {
        const val TAG = "CredentialUpdateExistingCredentialsDialog"
        const val KEY_URL = "url"
        const val KEY_CREDENTIALS = "credentials"
        const val RESULT_KEY_CREDENTIAL_RESULT_UPDATE = "CredentialUpdateExistingCredentialsResult"
    }
}

interface DialogFragmentType {
    fun asDialogFragment(): DialogFragment
}

interface CredentialAutofillDialogFactory {

    fun autofillSelectCredentialsDialog(url: String, credentials: List<LoginCredentials>): CredentialAutofillPickerDialog

    fun autofillSavingCredentialsDialog(url: String, credentials: LoginCredentials): CredentialSavePickerDialog

    fun autofillSavingUpdateCredentialsDialog(url: String, credentials: LoginCredentials): CredentialUpdateExistingCredentialsDialog

}
