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

package com.duckduckgo.autofill.impl.configuration.integration.modern.listener

import com.duckduckgo.autofill.api.AutofillUrlRequest
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType

class TestWebMessageListenerCallback : Callback {

    // for injection
    var credentialsToInject: List<LoginCredentials>? = null
    var credentialsAvailableToInject: Boolean? = null

    // for saving
    var credentialsToSave: LoginCredentials? = null

    // for password generation
    var offeredToGeneratePassword: Boolean = false

    // for email protection
    var showNativeChooseEmailAddressPrompt: Boolean = false
    var showNativeInContextEmailProtectionSignupPrompt: Boolean = false

    override suspend fun onCredentialsAvailableToInject(
        autofillUrlRequest: AutofillUrlRequest,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
    ) {
        credentialsAvailableToInject = true
        this.credentialsToInject = credentials
    }

    override suspend fun onCredentialsAvailableToSave(
        autofillUrlRequest: AutofillUrlRequest,
        credentials: LoginCredentials,
    ) {
        credentialsToSave = credentials
    }

    override suspend fun onGeneratedPasswordAvailableToUse(
        autofillUrlRequest: AutofillUrlRequest,
        username: String?,
        generatedPassword: String,
    ) {
        offeredToGeneratePassword = true
    }

    override fun showNativeChooseEmailAddressPrompt(autofillUrlRequest: AutofillUrlRequest) {
        showNativeChooseEmailAddressPrompt = true
    }

    override fun showNativeInContextEmailProtectionSignupPrompt(autofillUrlRequest: AutofillUrlRequest) {
        showNativeInContextEmailProtectionSignupPrompt = true
    }

    override fun onCredentialsSaved(savedCredentials: LoginCredentials) {
        // no-op
    }
}
