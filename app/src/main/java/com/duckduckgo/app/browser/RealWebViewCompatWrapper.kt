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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@SuppressLint(
    "RequiresFeature",
    "AddWebMessageListenerUsage",
    "AddDocumentStartJavaScriptUsage",
    "RemoveWebMessageListenerUsage",
)
@ContributesBinding(AppScope::class)
class RealWebViewCompatWrapper @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
) : WebViewCompatWrapper {
    override suspend fun addDocumentStartJavaScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandler? {
        return runCatching {
            if (!webViewCapabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)) {
                return null
            }

            if (webView is DuckDuckGoWebView) {
                return webView.safeAddDocumentStartJavaScript(script, allowedOriginRules)
            }
            return withContext(dispatcherProvider.main()) {
                WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOriginRules)
            }
        }.getOrElse { e ->
            logcat(ERROR) { "Error calling addDocumentStartJavaScript: ${e.asLog()}" }
            null
        }
    }

    override suspend fun removeWebMessageListener(webView: WebView, jsObjectName: String) {
        if (!webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)) {
            return
        }

        if (webView is DuckDuckGoWebView) {
            webView.safeRemoveWebMessageListener(jsObjectName)
            return
        }
        return withContext(dispatcherProvider.main()) {
            WebViewCompat.removeWebMessageListener(webView, jsObjectName)
        }
    }

    override suspend fun addWebMessageListener(
        webView: WebView,
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebViewCompat.WebMessageListener,
    ) {
        if (!webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener)) {
            return
        }

        if (webView is DuckDuckGoWebView) {
            webView.safeAddWebMessageListener(jsObjectName, allowedOriginRules, listener)
            return
        }
        return withContext(dispatcherProvider.main()) {
            WebViewCompat.addWebMessageListener(webView, jsObjectName, allowedOriginRules, listener)
        }
    }
}
