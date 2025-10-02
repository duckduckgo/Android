/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.impl.configuration.AutofillAvailableInputTypesProvider
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ResultHandlerImportPasswords @Inject constructor(
    private val autofillResponseWriter: AutofillResponseWriter,
    private val autofillAvailableInputTypesProvider: AutofillAvailableInputTypesProvider,
    private val dispatchers: DispatcherProvider,
    private val autofillMessagePoster: AutofillMessagePoster,
    private val previousPromoPrompts: InBrowserPromoPreviousPromptsStore,
) : AutofillFragmentResultsPlugin {

    override suspend fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
        webView: WebView?,
    ) {
        val originalUrl = result.getString(ImportPasswordsDialog.KEY_URL) ?: return
        storeUrlForPromptToAvoidAskingAgain(originalUrl)

        logcat { "Autofill: processing import passwords result for url=$originalUrl, tab=$tabId" }
        if (result.getBoolean(ImportPasswordsDialog.KEY_IMPORT_SUCCESS)) {
            logcat { "Autofill: refresh after import passwords success" }
            if (webView != null) {
                refreshAvailableInputTypes(webView, originalUrl)
            } else {
                logcat { "Autofill: cannot refresh available input types for url=$originalUrl, webView is null" }
            }
        } else {
            logcat { "Autofill: import didn't succeed; returning a 'no credential' response" }
            autofillCallback.onNoCredentialsChosenForAutofill(originalUrl)
        }
    }

    private suspend fun storeUrlForPromptToAvoidAskingAgain(originalUrl: String) {
        previousPromoPrompts.recordPromoDisplayed(originalUrl)
    }

    private suspend fun refreshAvailableInputTypes(
        webView: WebView,
        originalUrl: String,
    ) {
        withContext(dispatchers.io()) {
            val availableInputTypes = autofillAvailableInputTypesProvider.getTypes(originalUrl)
            val json = autofillResponseWriter.generateResponseNewAutofillDataAvailable(availableInputTypes)
            logcat { "Autofill: import completed; refresh request: $json" }
            autofillMessagePoster.postMessage(webView, json)
        }
    }

    override fun resultKey(tabId: String): String {
        return ImportPasswordsDialog.resultKey(tabId)
    }
}
