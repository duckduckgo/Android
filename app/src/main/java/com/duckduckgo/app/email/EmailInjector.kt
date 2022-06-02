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

import android.content.Context
import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.EmailJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Autofill
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import java.io.BufferedReader

interface EmailInjector {
    fun injectEmailAutofillJs(
        webView: WebView,
        url: String?
    )

    fun addJsInterface(
        webView: WebView,
        onTooltipShown: () -> Unit
    )

    fun injectAddressInEmailField(
        webView: WebView,
        alias: String?,
        url: String?
    )

    fun notifyWebAppSignEvent(
        webView: WebView,
        url: String?
    )
}

class EmailInjectorJs(
    private val emailManager: EmailManager,
    private val urlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider,
    private val featureToggle: FeatureToggle,
    private val autofill: Autofill,
) : EmailInjector {
    private val javaScriptInjector = JavaScriptInjector()

    override fun addJsInterface(
        webView: WebView,
        onTooltipShown: () -> Unit
    ) {
        // We always add the interface irrespectively if the feature is enabled or not
        webView.addJavascriptInterface(
            EmailJavascriptInterface(emailManager, webView, urlDetector, dispatcherProvider, featureToggle, autofill, onTooltipShown),
            JAVASCRIPT_INTERFACE_NAME
        )
    }

    @UiThread
    override fun injectEmailAutofillJs(
        webView: WebView,
        url: String?
    ) {
        url?.let {
            if (isDuckDuckGoUrl(url) || (isFeatureEnabled() && !autofill.isAnException(url) && emailManager.isSignedIn())) {
                webView.evaluateJavascript("javascript:${javaScriptInjector.getFunctionsJS()}", null)
            }
        }
    }

    @UiThread
    override fun injectAddressInEmailField(
        webView: WebView,
        alias: String?,
        url: String?
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
        url: String?
    ) {
        url?.let {
            if (isFeatureEnabled() && isDuckDuckGoUrl(url) && !emailManager.isSignedIn()) {
                webView.evaluateJavascript("javascript:${javaScriptInjector.getSignOutFunctions(webView.context)}", null)
            }
        }
    }

    private fun isFeatureEnabled() = featureToggle.isFeatureEnabled(PrivacyFeatureName.AutofillFeatureName, defaultValue = true)

    private fun isDuckDuckGoUrl(url: String?): Boolean = (url != null && urlDetector.isDuckDuckGoEmailUrl(url))

    private class JavaScriptInjector {
        private lateinit var functions: String
        private lateinit var aliasFunctions: String
        private lateinit var signOutFunctions: String

        fun getFunctionsJS(): String {
            if (!this::functions.isInitialized) {
                functions = loadJs("autofill.js")
            }
            return functions
        }

        fun getAliasFunctions(
            context: Context,
            alias: String?
        ): String {
            if (!this::aliasFunctions.isInitialized) {
                aliasFunctions = context.resources.openRawResource(R.raw.inject_alias).bufferedReader().use { it.readText() }
            }
            return aliasFunctions.replace("%s", alias.orEmpty())
        }

        fun getSignOutFunctions(
            context: Context
        ): String {
            if (!this::signOutFunctions.isInitialized) {
                signOutFunctions = context.resources.openRawResource(R.raw.signout_autofill).bufferedReader().use { it.readText() }
            }
            return signOutFunctions
        }

        fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

        private fun readResource(resourceName: String): BufferedReader? {
            return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
        }
    }
}
