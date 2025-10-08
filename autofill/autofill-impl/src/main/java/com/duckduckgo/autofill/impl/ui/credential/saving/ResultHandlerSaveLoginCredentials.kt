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

package com.duckduckgo.autofill.impl.ui.credential.saving

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ResultHandlerSaveLoginCredentials @Inject constructor(
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val dispatchers: DispatcherProvider,
    private val declineCounter: AutofillDeclineCounter,
    private val autofillStore: InternalAutofillStore,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
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

        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        val originalUrl = result.getString(CredentialSavePickerDialog.KEY_URL) ?: return
        val selectedCredentials =
            result.safeGetParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS) ?: return

        appCoroutineScope.launch(dispatchers.io()) {
            val savedCredentials = autofillStore.saveCredentials(originalUrl, selectedCredentials)
            if (savedCredentials != null) {
                declineCounter.disableDeclineCounter()

                withContext(dispatchers.main()) {
                    autofillCallback.onSavedCredentials(savedCredentials)
                }
            }
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
        return CredentialSavePickerDialog.resultKeyUserChoseToSaveCredentials(tabId)
    }
}
