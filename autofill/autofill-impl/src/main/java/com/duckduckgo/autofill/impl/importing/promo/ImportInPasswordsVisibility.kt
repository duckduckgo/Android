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

package com.duckduckgo.autofill.impl.importing.promo

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface ImportInPasswordsVisibility {
    fun canShowImportInPasswords(): Boolean
}

@ContributesBinding(AppScope::class)
class RealImportInPasswordsVisibility @Inject constructor(
    private val internalAutofillStore: InternalAutofillStore,
    private val autofillFeature: AutofillFeature,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ImportInPasswordsVisibility {

    private var canShowImportPasswords = false

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            Timber.i("Autofill: Evaluating if user can show import promo")
            evaluateIfUserCanShowImportPromo()
            Timber.i("Autofill: Evaluation result, can show import promo? $canShowImportPasswords")
        }
    }

    override fun canShowImportInPasswords(): Boolean {
        return canShowImportPasswords
    }

    private suspend fun evaluateIfUserCanShowImportPromo() {
        if (internalAutofillStore.hasEverImportedPasswords || internalAutofillStore.hasDismissedImportedPasswordsPromo) {
            canShowImportPasswords = false
            return
        }
        val gpmImport = autofillFeature.self().isEnabled() && autofillFeature.canImportFromGooglePasswordManager().isEnabled()
        val webViewWebMessageSupport = webViewCapabilityChecker.isSupported(WebMessageListener)
        val webViewDocumentStartJavascript = webViewCapabilityChecker.isSupported(DocumentStartJavaScript)
        canShowImportPasswords = gpmImport && webViewWebMessageSupport && webViewDocumentStartJavascript
    }
}
