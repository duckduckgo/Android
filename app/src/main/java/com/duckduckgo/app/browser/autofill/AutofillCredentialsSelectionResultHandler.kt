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

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.deviceauth.api.DeviceAuthenticator.Features.AUTOFILL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class AutofillCredentialsSelectionResultHandler @Inject constructor(
    private val deviceAuthenticator: DeviceAuthenticator,
    private val declineCounter: AutofillDeclineCounter,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autofillStore: AutofillStore
) {

    fun processAutofillCredentialSelectionResult(result: Bundle, browserTabFragment: Fragment, credentialInjector: CredentialInjector) {
        val originalUrl = result.getString(CredentialAutofillPickerDialog.KEY_URL) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            Timber.v("Autofill: User cancelled credential selection")
            credentialInjector.returnNoCredentialsWithPage(originalUrl)
            return
        }

        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialAutofillPickerDialog.KEY_CREDENTIALS) ?: return

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

    suspend fun processUserDeclined(context: Context, viewModel: BrowserTabViewModel) {
        withContext(dispatchers.main()) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.autofillDisableAutofillPromptTitle))
                .setMessage(context.getString(R.string.autofillDisableAutofillPromptMessage))
                .setPositiveButton(context.getString(R.string.autofillDisableAutofillPromptPositiveButton)) { _, _ -> disableDeclineCounter() }
                .setNegativeButton(context.getString(R.string.autofillDisableAutofillPromptNegativeButton)) { _, _ -> disableAutofill(viewModel) }
                .show()
        }
    }

    private fun disableDeclineCounter() {
        Timber.i("User selected to keep using autofill; will not prompt to disable again")
        appCoroutineScope.launch {
            declineCounter.disableDeclineCounter()
        }
    }

    private fun disableAutofill(viewModel: BrowserTabViewModel) {
        appCoroutineScope.launch {
            autofillStore.autofillEnabled = false
            declineCounter.disableDeclineCounter()
            refreshCurrentWebPageToDisableAutofill(viewModel)
            Timber.i("Autofill disabled at user request")
        }
    }

    suspend fun processSaveCredentialsResult(result: Bundle, credentialSaver: AutofillCredentialSaver): LoginCredentials? {
        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS) ?: return null
        val originalUrl = result.getString(CredentialSavePickerDialog.KEY_URL) ?: return null
        val savedCredentials = credentialSaver.saveCredentials(originalUrl, selectedCredentials)
        if (savedCredentials != null) {
            declineCounter.disableDeclineCounter()
        }
        return savedCredentials
    }

    suspend fun processUpdateCredentialsResult(result: Bundle, credentialSaver: AutofillCredentialSaver): LoginCredentials? {
        val selectedCredentials = result.getParcelable<LoginCredentials>(CredentialUpdateExistingCredentialsDialog.KEY_CREDENTIALS) ?: return null
        val originalUrl = result.getString(CredentialUpdateExistingCredentialsDialog.KEY_URL) ?: return null
        return credentialSaver.updateCredentials(originalUrl, selectedCredentials)
    }

    private suspend fun refreshCurrentWebPageToDisableAutofill(viewModel: BrowserTabViewModel) {
        withContext(dispatchers.main()) {
            viewModel.onRefreshRequested()
        }
    }

    interface CredentialInjector {
        fun shareCredentialsWithPage(originalUrl: String, credentials: LoginCredentials)
        fun returnNoCredentialsWithPage(originalUrl: String)
    }

    interface AutofillCredentialSaver {
        suspend fun saveCredentials(url: String, credentials: LoginCredentials): LoginCredentials?
        suspend fun updateCredentials(url: String, credentials: LoginCredentials): LoginCredentials?
    }
}
