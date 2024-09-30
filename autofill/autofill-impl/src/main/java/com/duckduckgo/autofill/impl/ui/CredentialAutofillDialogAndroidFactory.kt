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

package com.duckduckgo.autofill.impl.ui

import androidx.fragment.app.DialogFragment
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.email.EmailProtectionChooseEmailFragment
import com.duckduckgo.autofill.impl.email.incontext.prompt.EmailProtectionInContextSignUpPromptFragment
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillUseGeneratedPasswordDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class CredentialAutofillDialogAndroidFactory @Inject constructor() : CredentialAutofillDialogFactory {

    override fun autofillSelectCredentialsDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        tabId: String,
    ): DialogFragment {
        return AutofillSelectCredentialsDialogFragment.instance(autofillWebMessageRequest, credentials, triggerType, tabId)
    }

    override fun autofillSavingCredentialsDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillSavingCredentialsDialogFragment.instance(autofillWebMessageRequest, credentials, tabId)
    }

    override fun autofillSavingUpdatePasswordDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(
            autofillWebMessageRequest,
            credentials,
            tabId,
            CredentialUpdateType.Password,
        )
    }

    override fun autofillSavingUpdateUsernameDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(
            autofillWebMessageRequest,
            credentials,
            tabId,
            CredentialUpdateType.Username,
        )
    }

    override fun autofillGeneratePasswordDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        username: String?,
        generatedPassword: String,
        tabId: String,
    ): DialogFragment {
        return AutofillUseGeneratedPasswordDialogFragment.instance(autofillWebMessageRequest, username, generatedPassword, tabId)
    }

    override fun autofillEmailProtectionEmailChooserDialog(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        personalDuckAddress: String,
        tabId: String,
    ): DialogFragment {
        return EmailProtectionChooseEmailFragment.instance(
            personalDuckAddress = personalDuckAddress,
            url = autofillWebMessageRequest,
            tabId = tabId,
        )
    }

    override fun emailProtectionInContextSignUpDialog(tabId: String, autofillWebMessageRequest: AutofillWebMessageRequest): DialogFragment {
        return EmailProtectionInContextSignUpPromptFragment.instance(tabId, autofillWebMessageRequest)
    }
}
