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

package com.duckduckgo.autofill.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Used for configuring communication between JS and native code for autofill
 *
 * @param webMessageListeners responsible for handling incoming messages from JS and supporting ability to reply back.
 */
@ContributesBinding(AppScope::class)
class InlineBrowserAutofill @Inject constructor(
    private val autofillCapabilityChecker: InternalAutofillCapabilityChecker,
    private val dispatchers: DispatcherProvider,
    private val autofillJavascriptInjector: AutofillJavascriptInjector,
    private val webMessageListeners: PluginPoint<AutofillWebMessageListener>,
) : BrowserAutofill {

    override suspend fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        tabId: String,
    ) {
        if (autofillCapabilityChecker.webViewSupportsAutofill()) {
            withContext(dispatchers.io()) {
                configureModernIntegration(webView, autofillCallback, tabId)
            }
        } else {
            Timber.e("Modern javascript integration is not supported; autofill will not work")
        }
    }

    @SuppressLint("RequiresFeature")
    override fun removeJsInterface(webView: WebView?) {
        if (webView == null) return

        if (autofillCapabilityChecker.webViewSupportsAutofill()) {
            kotlin.runCatching {
                webMessageListeners.getPlugins().forEach {
                    WebViewCompat.removeWebMessageListener(webView, it.key)
                }
            }
        }
    }

    private suspend fun configureModernIntegration(
        webView: WebView,
        autofillCallback: Callback,
        tabId: String,
    ) {
        Timber.d("Autofill: Configuring modern integration with %d message listeners", webMessageListeners.getPlugins().size)

        webMessageListeners.getPlugins().forEach {
            Timber.v("Registering web message listener: %s -> %s", it.key, it::class.java.simpleName)
            webView.addWebMessageListener(it, autofillCallback, tabId)
        }

        autofillJavascriptInjector.addDocumentStartJavascript(webView)
    }

    override fun cancelPendingAutofillRequestToChooseCredentials() {
        webMessageListeners.getPlugins().forEach {
            it.cancelOutstandingRequests()
        }
    }

    @SuppressLint("RequiresFeature")
    private fun WebView.addWebMessageListener(
        messageListener: AutofillWebMessageListener,
        autofillCallback: Callback,
        tabId: String,
    ) {
        WebViewCompat.addWebMessageListener(this, messageListener.key, messageListener.origins, messageListener)
        messageListener.callback = autofillCallback
        messageListener.tabId = tabId
    }

    override fun notifyPageChanged() {
        webMessageListeners.getPlugins().forEach { it.cancelOutstandingRequests() }
    }
}
