/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.email.EmailJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeatureName
import com.duckduckgo.feature.toggles.api.FeatureToggle

interface EmailInjector {

    fun addJsInterface(
        webView: WebView,
        onTooltipShown: () -> Unit,
    )

    fun injectAddressInEmailField(
        webView: WebView,
        alias: String?,
        url: String?,
    )

    fun notifyWebAppSignEvent(
        webView: WebView,
        url: String?,
    )
}

class EmailInjectorJs(
    private val emailManager: EmailManager,
    private val urlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider,
    private val featureToggle: FeatureToggle,
    private val javaScriptInjector: JavascriptInjector,
    private val autofill: Autofill,
) : EmailInjector {

    override fun addJsInterface(
        webView: WebView,
        onTooltipShown: () -> Unit,
    ) {
        // We always add the interface irrespectively if the feature is enabled or not
        webView.addJavascriptInterface(
            EmailJavascriptInterface(emailManager, webView, urlDetector, dispatcherProvider, featureToggle, autofill, onTooltipShown),
            JAVASCRIPT_INTERFACE_NAME,
        )
    }

    @UiThread
    override fun injectAddressInEmailField(
        webView: WebView,
        alias: String?,
        url: String?,
    ) {
        url?.let {
            if (isFeatureEnabled() && !autofill.isAnException(url)) {
                webView.evaluateJavascript("javascript:${javaScriptInjector.getAliasFunctions(webView.context, alias)}", null)
            }
        }
    }

    @UiThread
    override fun notifyWebAppSignEvent(
        webView: WebView,
        url: String?,
    ) {
        url?.let {
            if (isFeatureEnabled() && isDuckDuckGoUrl(url) && !emailManager.isSignedIn()) {
                webView.evaluateJavascript("javascript:${javaScriptInjector.getSignOutFunctions(webView.context)}", null)
            }
        }
    }

    private fun isFeatureEnabled() = featureToggle.isFeatureEnabled(AutofillFeatureName.Autofill.value, defaultValue = true)

    private fun isDuckDuckGoUrl(url: String?): Boolean = (url != null && urlDetector.isDuckDuckGoEmailUrl(url))
}
