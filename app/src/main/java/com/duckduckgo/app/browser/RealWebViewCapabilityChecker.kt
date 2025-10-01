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

import androidx.webkit.WebViewFeature
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.compareSemanticVersion
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealWebViewCapabilityChecker @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val webViewVersionProvider: WebViewVersionProvider,
) : WebViewCapabilityChecker {
    override suspend fun isSupported(capability: WebViewCapability): Boolean =
        when (capability) {
            DocumentStartJavaScript -> isDocumentStartJavaScriptSupported()
            WebMessageListener -> isWebMessageListenerSupported()
        }

    private suspend fun isWebMessageListenerSupported(): Boolean =
        withContext(dispatchers.io()) {
            webViewVersionProvider
                .getFullVersion()
                .compareSemanticVersion(WEB_MESSAGE_LISTENER_WEBVIEW_VERSION)
                ?.let { it >= 0 } ?: false
        } && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    private fun isDocumentStartJavaScriptSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    companion object {
        // critical fixes didn't exist until this WebView version. See https://issues.chromium.org/issues/338340758#comment42
        private const val WEB_MESSAGE_LISTENER_WEBVIEW_VERSION = "126.0.6478.40"
    }
}
