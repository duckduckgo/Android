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
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeatureName
import com.duckduckgo.autofill.api.BrowserAutofill.Configurator
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class InlineBrowserAutofillConfigurator @Inject constructor(
    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider,
    private val javascriptInjector: JavascriptInjector,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val autofill: Autofill,
    private val featureToggle: FeatureToggle,
) : Configurator {
    override fun configureAutofillForCurrentPage(
        webView: WebView,
        url: String?,
    ) {
        if (canJsBeInjected(url)) {
            coroutineScope.launch(dispatchers.io()) {
                val rawJs = javascriptInjector.getFunctionsJS()
                val formatted = autofillRuntimeConfigProvider.getRuntimeConfiguration(rawJs, url)

                withContext(dispatchers.main()) {
                    webView.evaluateJavascript("javascript:$formatted", null)
                }
            }
        }
    }

    private fun canJsBeInjected(url: String?): Boolean {
        url?.let {
            return (isFeatureEnabled() && !autofill.isAnException(url))
        }
        return false
    }

    private fun isFeatureEnabled() = featureToggle.isFeatureEnabled(AutofillFeatureName.Autofill.value, defaultValue = true)
}
