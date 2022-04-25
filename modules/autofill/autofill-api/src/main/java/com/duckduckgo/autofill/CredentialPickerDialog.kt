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

interface CredentialAutofillPickerDialog {
    fun asDialogFragment(): DialogFragment

    companion object {
        const val TAG = "CredentialAutofillPickerDialog"
        const val RESULT_KEY_CREDENTIAL_PICKER = "CredentialAutofillPickerDialogResult"
    }
}

interface CredentialAutofillPickerDialogFactory {
    fun create(
        url: String,
        credentials: List<Credentials>
    ): CredentialAutofillPickerDialog
}
