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

package com.duckduckgo.autofill.ui

import com.duckduckgo.autofill.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials

class CredentialAutofillDialogAndroidFactory : CredentialAutofillDialogFactory {

    override fun credentialAutofillPickerDialog(url: String, credentials: List<LoginCredentials>): CredentialAutofillPickerDialog {
        return CredentialAutofillPickerDialogFragment.instance(url, credentials)
    }

    override fun credentialAutofillSavingDialog(url: String, credentials: LoginCredentials): CredentialSavePickerDialog {
        return CredentialAutofillSavingDialogFragment.instance(url, credentials)
    }
}
