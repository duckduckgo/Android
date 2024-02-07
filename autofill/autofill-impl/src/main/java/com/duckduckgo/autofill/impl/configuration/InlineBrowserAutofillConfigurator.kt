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
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesBinding(AppScope::class)
class InlineBrowserAutofillConfigurator @Inject constructor(
    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val autofillJavascriptLoader: AutofillJavascriptLoader,
    private val messagePoster: AutofillMessagePoster,
) : Configurator {
    override fun configureAutofillForCurrentPage(
        webView: WebView,
        url: String?,
    ) {
        coroutineScope.launch(dispatchers.io()) {
            if (canJsBeInjected(url)) {
                Timber.v("Injecting autofill JS into WebView for %s", url)

                //val rawJs = autofillJavascriptLoader.getAutofillJavascript()
                val formatted = autofillRuntimeConfigProvider.getRuntimeConfiguration(url)
                val fullJs = """
                    {
                       "type": "getAutofillConfigResponse",
                       "success" : $formatted
                    }
                """.trimIndent().also {
                    Timber.w("cdr json to send $it")
                }

                withContext(dispatchers.main()) {
                    messagePoster.postMessage(webView, fullJs)
                    //webView.evaluateJavascript("javascript:$formatted", null)
                }
            } else {
                Timber.v("Won't inject autofill JS into WebView for: %s", url)
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
