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
import com.duckduckgo.autofill.api.AutofillUrlRequest
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
        autofillUrlRequest: AutofillUrlRequest,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        tabId: String,
    ): DialogFragment {
        return AutofillSelectCredentialsDialogFragment.instance(autofillUrlRequest, credentials, triggerType, tabId)
    }

    override fun autofillSavingCredentialsDialog(
        autofillUrlRequest: AutofillUrlRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillSavingCredentialsDialogFragment.instance(autofillUrlRequest, credentials, tabId)
    }

    override fun autofillSavingUpdatePasswordDialog(
        autofillUrlRequest: AutofillUrlRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(
            autofillUrlRequest,
            credentials,
            tabId,
            CredentialUpdateType.Password,
        )
    }

    override fun autofillSavingUpdateUsernameDialog(
        autofillUrlRequest: AutofillUrlRequest,
        credentials: LoginCredentials,
        tabId: String,
    ): DialogFragment {
        return AutofillUpdatingExistingCredentialsDialogFragment.instance(
            autofillUrlRequest,
            credentials,
            tabId,
            CredentialUpdateType.Username,
        )
    }

    override fun autofillGeneratePasswordDialog(
        autofillUrlRequest: AutofillUrlRequest,
        username: String?,
        generatedPassword: String,
        tabId: String,
    ): DialogFragment {
        return AutofillUseGeneratedPasswordDialogFragment.instance(autofillUrlRequest, username, generatedPassword, tabId)
    }

    override fun autofillEmailProtectionEmailChooserDialog(
        autofillUrlRequest: AutofillUrlRequest,
        personalDuckAddress: String,
        tabId: String,
    ): DialogFragment {
        return EmailProtectionChooseEmailFragment.instance(
            personalDuckAddress = personalDuckAddress,
            url = autofillUrlRequest,
            tabId = tabId,
        )
    }

    override fun emailProtectionInContextSignUpDialog(tabId: String, autofillUrlRequest: AutofillUrlRequest): DialogFragment {
        return EmailProtectionInContextSignUpPromptFragment.instance(tabId, autofillUrlRequest)
    }
}
