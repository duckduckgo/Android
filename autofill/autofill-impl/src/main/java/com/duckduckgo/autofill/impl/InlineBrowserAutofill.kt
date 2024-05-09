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
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber

@ContributesBinding(FragmentScope::class)
class InlineBrowserAutofill @Inject constructor(
    private val autofillCapabilityChecker: InternalAutofillCapabilityChecker,
    private val dispatchers: DispatcherProvider,
    private val autofillJavascriptInjector: AutofillJavascriptInjector,
    private val webMessageListeners: PluginPoint<AutofillWebMessageListener>,
    private val autofillFeature: AutofillFeature,
    private val webMessageAttacher: AutofillWebMessageAttacher,
) : BrowserAutofill {

    override suspend fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        tabId: String,
    ) {
        withContext(dispatchers.io()) {
            if (!autofillCapabilityChecker.webViewSupportsAutofill()) {
                Timber.e("Modern javascript integration is not supported on this WebView version; autofill will not work")
                return@withContext
            }

            if (!autofillFeature.self().isEnabled()) {
                Timber.w("Autofill feature is not enabled in remote config; autofill will not work")
                return@withContext
            }

            configureModernIntegration(webView, autofillCallback, tabId)
        }
    }

    private suspend fun configureModernIntegration(
        webView: WebView,
        autofillCallback: Callback,
        tabId: String,
    ) {
        Timber.d("Autofill: Configuring modern integration with %d message listeners", webMessageListeners.getPlugins().size)

        withContext(dispatchers.main()) {
            webMessageListeners.getPlugins().forEach {
                webView.addWebMessageListener(it, autofillCallback, tabId)
                yield()
            }

            autofillJavascriptInjector.addDocumentStartJavascript(webView)
        }
    }

    override fun cancelPendingAutofillRequestToChooseCredentials() {
        webMessageListeners.getPlugins().forEach {
            it.cancelOutstandingRequests()
        }
    }

    private fun WebView.addWebMessageListener(
        messageListener: AutofillWebMessageListener,
        autofillCallback: Callback,
        tabId: String,
    ) {
        webMessageAttacher.addListener(this, messageListener)
        messageListener.callback = autofillCallback
        messageListener.tabId = tabId
    }

    override fun notifyPageChanged() {
        webMessageListeners.getPlugins().forEach { it.cancelOutstandingRequests() }
    }
}

interface AutofillWebMessageAttacher {
    fun addListener(
        webView: WebView,
        listener: AutofillWebMessageListener,
    )
}

@SuppressLint("RequiresFeature")
@ContributesBinding(FragmentScope::class)
class AutofillWebMessageAttacherImpl @Inject constructor() : AutofillWebMessageAttacher {

    override fun addListener(
        webView: WebView,
        listener: AutofillWebMessageListener,
    ) {
        WebViewCompat.addWebMessageListener(webView, listener.key, listener.origins, listener)
    }
}
