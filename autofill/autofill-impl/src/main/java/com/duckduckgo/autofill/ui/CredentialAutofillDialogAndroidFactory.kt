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

import androidx.fragment.app.DialogFragment
import com.duckduckgo.autofill.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.domain.app.LoginTriggerType
import com.duckduckgo.autofill.ui.credential.saving.AutofillSavingCredentialsDialogFragment
import com.duckduckgo.autofill.ui.credential.selecting.AutofillSelectCredentialsDialogFragment
import com.duckduckgo.autofill.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class CredentialAutofillDialogAndroidFactory @Inject constructor() : CredentialAutofillDialogFactory {

    override fun autofillSelectCredentialsDialog(
        url: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        tabId: String
    ): DialogFragment {
        return AutofillSelectCredentialsDialogFragment.instance(url, credentials, triggerType, tabId)
    }

    override fun autofillSavingCredentialsDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String
    ): DialogFragment {
        return AutofillSavingCredentialsDialogFragment.instance(url, credentials, tabId)
    }

    override fun autofillSavingUpdatePasswordDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(url, credentials, tabId, CredentialUpdateType.Password)
    }

    override fun autofillSavingUpdateUsernameDialog(
        url: String,
        credentials: LoginCredentials,
        tabId: String
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(url, credentials, tabId, CredentialUpdateType.Username)
    }
}
