/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.gpm.webflow.autofill

import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.domain.app.LoginCredentials

interface ImportGooglePasswordAutofillCallback : Callback {

    override suspend fun onGeneratedPasswordAvailableToUse(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        username: String?,
        generatedPassword: String,
    ) {}

    override fun onCredentialsSaved(savedCredentials: LoginCredentials) {}

    override fun showNativeChooseEmailAddressPrompt(autofillWebMessageRequest: AutofillWebMessageRequest) {}
    override suspend fun onCredentialsAvailableToSave(autofillWebMessageRequest: AutofillWebMessageRequest, credentials: LoginCredentials) {}
    override fun showNativeInContextEmailProtectionSignupPrompt(autofillWebMessageRequest: AutofillWebMessageRequest) {}
}

interface ImportGooglePasswordAutofillEventListener : AutofillEventListener {
    override fun onSelectedToSignUpForInContextEmailProtection(autofillWebMessageRequest: AutofillWebMessageRequest) {}
    override fun onSavedCredentials(credentials: LoginCredentials) {}
    override fun onUpdatedCredentials(credentials: LoginCredentials) {}
    override fun onAutofillStateChange() {}
}
