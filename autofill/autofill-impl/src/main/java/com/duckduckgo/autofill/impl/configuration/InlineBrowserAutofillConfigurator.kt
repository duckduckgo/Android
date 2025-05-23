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
package com.duckduckgo.autofill.impl.configuration

import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.BrowserAutofill.Configurator
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesBinding(AppScope::class)
class InlineBrowserAutofillConfigurator @Inject constructor(
    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val autofillJavascriptLoader: AutofillJavascriptLoader,
) : Configurator {
    override fun configureAutofillForCurrentPage(
        webView: WebView,
        url: String?,
    ) {
        coroutineScope.launch(dispatchers.io()) {
            if (canJsBeInjected(url)) {
                logcat(VERBOSE) { "Injecting autofill JS into WebView for $url" }

                val rawJs = autofillJavascriptLoader.getAutofillJavascript()
                val formatted = autofillRuntimeConfigProvider.getRuntimeConfiguration(rawJs, url)

                withContext(dispatchers.main()) {
                    webView.evaluateJavascript("javascript:$formatted", null)
                }
            } else {
                logcat(VERBOSE) { "Won't inject autofill JS into WebView for: $url" }
            }
        }
    }

    private suspend fun canJsBeInjected(url: String?): Boolean {
        url?.let {
            // note, we don't check for autofillEnabledByUser here, as the user-facing preference doesn't cover email
            return autofillCapabilityChecker.isAutofillEnabledByConfiguration(it)
        }
        return false
    }
}
