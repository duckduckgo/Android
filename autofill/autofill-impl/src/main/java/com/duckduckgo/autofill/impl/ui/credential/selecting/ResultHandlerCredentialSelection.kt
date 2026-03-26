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
import android.os.Bundle
import android.os.Parcelable
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ResultHandlerCredentialSelection @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val appBuildConfig: AppBuildConfig,
    private val autofillStore: InternalAutofillStore,
    private val autofilledListeners: PluginPoint<DataAutofilledListener>,
) : AutofillFragmentResultsPlugin {

    override suspend fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
        webView: WebView?,
    ) {
        logcat { "${this::class.java.simpleName}: processing result" }

        val originalUrl = result.getString(CredentialAutofillPickerDialog.KEY_URL) ?: return

        if (result.getBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED)) {
            logcat(VERBOSE) { "Autofill: User cancelled credential selection" }
            autofillCallback.onNoCredentialsChosenForAutofill(originalUrl)
            return
        }

        appCoroutineScope.launch(dispatchers.io()) {
            processAutofillCredentialSelectionResult(
                result = result,
                browserTabFragment = fragment,
                autofillCallback = autofillCallback,
                originalUrl = originalUrl,
            )
        }
    }

    private suspend fun processAutofillCredentialSelectionResult(
        result: Bundle,
        browserTabFragment: Fragment,
        autofillCallback: AutofillEventListener,
        originalUrl: String,
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
                        logcat(VERBOSE) { "Autofill: user selected credential to use, and successfully authenticated" }
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_SUCCESSFUL)
                        notifyAutofilledListeners()
                        autofillCallback.onShareCredentialsForAutofill(originalUrl, selectedCredentials)
                    }

                    DeviceAuthenticator.AuthResult.UserCancelled -> {
                        logcat { "Autofill: user selected credential to use, but cancelled without authenticating" }
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_CANCELLED)
                        autofillCallback.onNoCredentialsChosenForAutofill(originalUrl)
                    }

                    is DeviceAuthenticator.AuthResult.Error -> {
                        logcat(WARN) { "Autofill: user selected credential to use, but there was an error when authenticating: ${it.reason}" }
                        pixel.fire(AutofillPixelNames.AUTOFILL_AUTHENTICATION_TO_AUTOFILL_AUTH_FAILURE)
                        autofillCallback.onNoCredentialsChosenForAutofill(originalUrl)
                    }
                }
            }
        }
    }

    private fun notifyAutofilledListeners() {
        autofilledListeners.getPlugins().forEach {
            it.onAutofilledSavedPassword()
        }
    }

    private fun LoginCredentials.updateLastUsedTimestamp() {
        appCoroutineScope.launch(dispatchers.io()) {
            val updated = this@updateLastUsedTimestamp.copy(lastUsedMillis = System.currentTimeMillis())
            autofillStore.updateCredentials(updated, refreshLastUpdatedTimestamp = false)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private inline fun <reified T : Parcelable> Bundle.safeGetParcelable(key: String) =
        if (appBuildConfig.sdkInt >= 33) {
            getParcelable(key, T::class.java)
        } else {
            getParcelable(key)
        }

    override fun resultKey(tabId: String): String {
        return CredentialAutofillPickerDialog.resultKey(tabId)
    }
}
