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

package com.duckduckgo.app.browser.autofill

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL
import timber.log.Timber
import javax.inject.Inject

class AutofillCredentialsSelectionResultHandler @Inject constructor(private val deviceAuthenticator: DeviceAuthenticator) {

    fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: Fragment,
        credentialInjector: CredentialInjector,
    ) {
        val originalUrl = result.getString(CredentialAutofillPickerDialog.KEY_URL) ?: return
        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialAutofillPickerDialog.KEY_CREDENTIALS) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            Timber.v("Autofill: User cancelled credential selection")
            credentialInjector.returnNoCredentialsWithPage(originalUrl)
            return
        }

        deviceAuthenticator.authenticate(AUTOFILL, browserTabFragment) {
            val successfullyAuthenticated = it is Success
            Timber.v("Autofill: user selected credential to use. Successfully authenticated: %s", successfullyAuthenticated)

            if (successfullyAuthenticated) {
                credentialInjector.shareCredentialsWithPage(originalUrl, selectedCredentials)
            } else {
                credentialInjector.returnNoCredentialsWithPage(originalUrl)
            }
        }
    }

    fun processSaveCredentialsResult(result: Bundle, credentialSaver: AutofillCredentialSaver) {
        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS) ?: return
        val originalUrl = result.getString(CredentialSavePickerDialog.KEY_URL) ?: return
        credentialSaver.saveCredentials(originalUrl, selectedCredentials)
    }

    fun processUpdateCredentialsResult(result: Bundle, credentialSaver: AutofillCredentialSaver) {
        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS) ?: return
        val originalUrl = result.getString(CredentialUpdateExistingCredentialsDialog.KEY_URL) ?: return
        credentialSaver.updateCredentials(originalUrl, selectedCredentials)
    }

    interface CredentialInjector {
        fun shareCredentialsWithPage(originalUrl: String, credentials: LoginCredentials)
        fun returnNoCredentialsWithPage(originalUrl: String)
    }

    interface AutofillCredentialSaver {
        fun saveCredentials(url: String, credentials: LoginCredentials)
        fun updateCredentials(url: String, credentials: LoginCredentials)
    }
}
