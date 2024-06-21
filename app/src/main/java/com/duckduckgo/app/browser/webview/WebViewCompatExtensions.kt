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

package com.duckduckgo.app.browser.webview

import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import androidx.webkit.WebViewFeature
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.compareSemanticVersion
import kotlinx.coroutines.withContext

const val WEB_MESSAGE_LISTENER_WEBVIEW_VERSION = "126.0.6478.40"

suspend fun safeAddWebMessageListener(
    dispatchers: DispatcherProvider,
    webViewVersionProvider: WebViewVersionProvider,
    jsObjectName: String,
    allowedOriginRules: Set<String>,
    listener: WebMessageListener,
) {
    val isSupportedWebViewVersion = withContext(dispatchers.io()) {
        webViewVersionProvider.getFullVersion()
            .compareSemanticVersion(WEB_MESSAGE_LISTENER_WEBVIEW_VERSION)?.let { it >= 0 } ?: false
    }

    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        isSupportedWebViewVersion &&
        !this.isDestroyed
    ) {
        WebViewCompat.addWebMessageListener(
            this,
            jsObjectName,
            allowedOriginRules,
            listener,
        )
    }
}
