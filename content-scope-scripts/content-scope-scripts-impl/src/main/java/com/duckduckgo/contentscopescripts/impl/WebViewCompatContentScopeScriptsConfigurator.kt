/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface WebViewCompatContentScopeScriptsConfigurator {
    suspend fun isEnabled(): Boolean

    suspend fun isWebMessagingEnabled(): Boolean

    suspend fun configure(
        webView: WebView,
        jsMessageCallback: WebViewCompatMessageCallback,
    )

    suspend fun cleanup(webView: WebView)

    suspend fun refreshDocumentStartScripts(webView: WebView)
}

@ContributesBinding(FragmentScope::class)
@SingleInstanceIn(FragmentScope::class)
class RealWebViewCompatContentScopeScriptsConfigurator @Inject constructor(
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val documentStartPlugins: PluginPoint<AddDocumentStartJavaScriptPlugin>,
    private val webMessagingPlugins: PluginPoint<WebMessagingPlugin>,
    private val contentScopeExperiments: ContentScopeExperiments,
) : WebViewCompatContentScopeScriptsConfigurator {

    override suspend fun isEnabled(): Boolean = webViewCompatContentScopeScripts.isEnabled()

    override suspend fun isWebMessagingEnabled(): Boolean = webViewCompatContentScopeScripts.isWebMessagingEnabled()

    override suspend fun configure(
        webView: WebView,
        jsMessageCallback: WebViewCompatMessageCallback,
    ) {
        if (!isEnabled()) {
            return
        }

        refreshDocumentStartScripts(webView)

        if (isWebMessagingEnabled()) {
            webMessagingPlugins.getPlugins().forEach { plugin ->
                plugin.register(jsMessageCallback, webView)
            }
        }
    }

    override suspend fun cleanup(webView: WebView) {
        if (!isWebMessagingEnabled()) {
            return
        }

        webMessagingPlugins.getPlugins().forEach { plugin ->
            plugin.unregister(webView)
        }
    }

    override suspend fun refreshDocumentStartScripts(webView: WebView) {
        if (!isEnabled()) {
            return
        }

        documentStartPlugins.getPlugins().forEach { plugin ->
            plugin.addDocumentStartJavaScript(webView)
        }
    }
}
