/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_CREDENTIALS
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_URL_REQUEST
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.domain.javascript.JavascriptCredentials
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(FragmentScope::class)
class ResultHandlerCredentialSelection @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val autofillStore: InternalAutofillStore,
    private val messagePoster: AutofillMessagePoster,
    private val autofillResponseWriter: AutofillResponseWriter,
) : AutofillFragmentResultsPlugin {

    override fun resultKey(tabId: String): String {
        return CredentialAutofillPickerDialog.resultKey(tabId)
    }

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val autofillWebMessageRequest = BundleCompat.getParcelable(result, KEY_URL_REQUEST, AutofillWebMessageRequest::class.java) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            Timber.v("Autofill: User cancelled credential selection")
            injectNoCredentials(autofillWebMessageRequest)
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            processAutofillCredentialSelectionResult(
                result = result,
                browserTabFragment = fragment,
                autofillWebMessageRequest = autofillWebMessageRequest,
            )
        }
    }

    private fun injectCredentials(
        credentials: LoginCredentials,
        autofillWebMessageRequest: AutofillWebMessageRequest,
    ) {
        Timber.v("Informing JS layer with credentials selected")
        appCoroutineScope.launch(dispatchers.io()) {
            val jsCredentials = credentials.asJsCredentials()
            val jsonResponse = autofillResponseWriter.generateResponseGetAutofillData(jsCredentials)
            messagePoster.postMessage(jsonResponse, autofillWebMessageRequest.requestId)
        }
    }

    private fun injectNoCredentials(autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.v("No credentials selected; informing JS layer")
        appCoroutineScope.launch(dispatchers.io()) {
            val jsonResponse = autofillResponseWriter.generateEmptyResponseGetAutofillData()
            messagePoster.postMessage(jsonResponse, autofillWebMessageRequest.requestId)
        }
    }

    private suspend fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: Fragment,
        autofillWebMessageRequest: AutofillWebMessageRequest,
    ) {
        val selectedCredentials = BundleCompat.getParcelable(result, KEY_CREDENTIALS, LoginCredentials::class.java) ?: return

        selectedCredentials.updateLastUsedTimestamp()

        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_SHOWN)

        withContext(dispatchers.main()) {
            deviceAuthenticator.authenticate(
                browserTabFragment,
            ) {
                when (it) {
                    DeviceAuthenticator.AuthResult.Success -> {
                        Timber.v("Autofill: user selected credential to use, and successfully authenticated")
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL)
                        injectCredentials(selectedCredentials, autofillWebMessageRequest)
                    }

                    DeviceAuthenticator.AuthResult.UserCancelled -> {
                        Timber.d("Autofill: user selected credential to use, but cancelled without authenticating")
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED)
                        injectNoCredentials(autofillWebMessageRequest)
                    }

                    is DeviceAuthenticator.AuthResult.Error -> {
                        Timber.w("Autofill: user selected credential to use, but there was an error when authenticating: ${it.reason}")
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE)
                        injectNoCredentials(autofillWebMessageRequest)
                    }
                }
            }
        }
    }

    private fun LoginCredentials.updateLastUsedTimestamp() {
        appCoroutineScope.launch(dispatchers.io()) {
            val updated = this@updateLastUsedTimestamp.copy(lastUsedMillis = System.currentTimeMillis())
            autofillStore.updateCredentials(updated, refreshLastUpdatedTimestamp = false)
        }
    }

    private fun LoginCredentials.asJsCredentials(): JavascriptCredentials {
        return JavascriptCredentials(
            username = username,
            password = password,
        )
    }
}
