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
import java.io.BufferedReader

interface EmailInjector {
    fun injectEmailAutofillJs(webView: WebView, url: String?)
    fun addJsInterface(webView: WebView, onTooltipShown: () -> Unit)
    fun injectAddressInEmailField(webView: WebView, alias: String?)
}

class EmailInjectorJs(private val emailManager: EmailManager, private val urlDetector: DuckDuckGoUrlDetector, private val dispatcherProvider: DispatcherProvider) : EmailInjector {
    private val javaScriptInjector = JavaScriptInjector()

    override fun addJsInterface(webView: WebView, onTooltipShown: () -> Unit) {
        webView.addJavascriptInterface(EmailJavascriptInterface(emailManager, onTooltipShown, webView, urlDetector, dispatcherProvider), JAVASCRIPT_INTERFACE_NAME)
    }

    @UiThread
    override fun injectEmailAutofillJs(webView: WebView, url: String?) {
        if (isDuckDuckGoUrl(url) || emailManager.isSignedIn()) {
            webView.evaluateJavascript("javascript:${javaScriptInjector.getFunctionsJS()}", null)
        }
    }

    @UiThread
    override fun injectAddressInEmailField(webView: WebView, alias: String?) {
        webView.evaluateJavascript("javascript:${javaScriptInjector.getAliasFunctions(webView.context, alias)}", null)
    }

    private fun isDuckDuckGoUrl(url: String?): Boolean = (url != null && urlDetector.isDuckDuckGoEmailUrl(url))

    private class JavaScriptInjector {
        private lateinit var functions: String
        private lateinit var aliasFunctions: String

        fun getFunctionsJS(): String {
            if (!this::functions.isInitialized) {
                functions = loadJs("autofill.js")
            }
            return functions
        }

        fun getAliasFunctions(context: Context, alias: String?): String {
            if (!this::aliasFunctions.isInitialized) {
                aliasFunctions = context.resources.openRawResource(R.raw.inject_alias).bufferedReader().use { it.readText() }
            }
            return aliasFunctions.replace("%s", alias.orEmpty())
        }

        fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

        private fun readResource(resourceName: String): BufferedReader? {
            return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
        }
    }
}
