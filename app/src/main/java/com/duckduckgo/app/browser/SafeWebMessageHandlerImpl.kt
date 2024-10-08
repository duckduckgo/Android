/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.app.browser.api.SafeWebMessageHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

@SuppressLint("RequiresFeature", "AddWebMessageListenerUsage", "RemoveWebMessageListenerUsage")
@ContributesBinding(AppScope::class)
class SafeWebMessageHandlerImpl @Inject constructor(
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
) : SafeWebMessageHandler {

    override suspend fun addWebMessageListener(
        webView: WebView,
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebMessageListener,
    ): Boolean = runCatching {
        if (webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener) && !isDestroyed(webView)) {
            WebViewCompat.addWebMessageListener(webView, jsObjectName, allowedOriginRules, listener)
            true
        } else {
            false
        }
    }.getOrElse { exception ->
        Timber.e(exception, "Error adding WebMessageListener: $jsObjectName")
        false
    }

    override suspend fun removeWebMessageListener(
        webView: WebView,
        jsObjectName: String,
    ): Boolean = runCatching {
        if (webViewCapabilityChecker.isSupported(WebViewCapability.WebMessageListener) && !isDestroyed(webView)) {
            WebViewCompat.removeWebMessageListener(webView, jsObjectName)
            true
        } else {
            false
        }
    }.getOrElse { exception ->
        Timber.e(exception, "Error removing WebMessageListener: $jsObjectName")
        false
    }

    /**
     * Can only check destroyed flag for DuckDuckGoWebView for now. If a normal WebView, assume not destroyed.
     */
    private fun isDestroyed(webView: WebView): Boolean {
        return if (webView is DuckDuckGoWebView) {
            webView.isDestroyed
        } else {
            false
        }
    }
}
