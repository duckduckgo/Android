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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillUrlRequest
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
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
    private val appBuildConfig: AppBuildConfig,
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

        val autofillUrlRequest = result.safeGetParcelable<AutofillUrlRequest>(CredentialAutofillPickerDialog.KEY_URL_REQUEST) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            Timber.v("Autofill: User cancelled credential selection")
            injectNoCredentials(autofillUrlRequest)
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            processAutofillCredentialSelectionResult(
                result = result,
                browserTabFragment = fragment,
                autofillUrlRequest = autofillUrlRequest,
            )
        }
    }

    private fun injectCredentials(
        credentials: LoginCredentials,
        autofillUrlRequest: AutofillUrlRequest,
    ) {
        Timber.v("Informing JS layer with credentials selected")
        appCoroutineScope.launch(dispatchers.io()) {
            val jsCredentials = credentials.asJsCredentials()
            val jsonResponse = autofillResponseWriter.generateResponseGetAutofillData(jsCredentials)
            Timber.i("Injecting credentials: %s", jsonResponse)
            messagePoster.postMessage(jsonResponse, autofillUrlRequest.requestId)
        }
    }

    private fun injectNoCredentials(autofillUrlRequest: AutofillUrlRequest) {
        Timber.v("No credentials selected; informing JS layer")
        appCoroutineScope.launch(dispatchers.io()) {
            val jsonResponse = autofillResponseWriter.generateEmptyResponseGetAutofillData()
            messagePoster.postMessage(jsonResponse, autofillUrlRequest.requestId)
        }
    }

    private suspend fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: Fragment,
        autofillUrlRequest: AutofillUrlRequest,
    ) {
        val selectedCredentials: LoginCredentials =
            result.safeGetParcelable(CredentialAutofillPickerDialog.KEY_CREDENTIALS) ?: return

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
                        injectCredentials(selectedCredentials, autofillUrlRequest)
                    }

                    DeviceAuthenticator.AuthResult.UserCancelled -> {
                        Timber.d("Autofill: user selected credential to use, but cancelled without authenticating")
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED)
                        injectNoCredentials(autofillUrlRequest)
                    }

                    is DeviceAuthenticator.AuthResult.Error -> {
                        Timber.w("Autofill: user selected credential to use, but there was an error when authenticating: ${it.reason}")
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE)
                        injectNoCredentials(autofillUrlRequest)
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

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private inline fun <reified T : Parcelable> Bundle.safeGetParcelable(key: String) =
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            getParcelable(key)
        }
}
