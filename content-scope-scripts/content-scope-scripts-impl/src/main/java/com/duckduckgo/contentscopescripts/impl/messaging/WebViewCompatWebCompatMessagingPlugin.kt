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

package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

private const val JS_OBJECT_NAME = "contentScopeAdsjs"

@ContributesMultibinding(ActivityScope::class)
class WebViewCompatWebCompatMessagingPlugin @Inject constructor(
    private val handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin>,
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : WebMessagingPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private val context: String = "contentScopeScripts"
    private val allowedDomains: Set<String> = setOf("*")

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun process(
        message: String,
        jsMessageCallback: JsMessageCallback,
    ) {
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)

            jsMessage?.let {
                if (context == jsMessage.context) {
                    // Process global handlers first (always processed regardless of feature handlers)
                    globalHandlers.getPlugins()
                        .map { it.getGlobalJsMessageHandler() }
                        .filter { it.method == jsMessage.method }
                        .forEach { handler ->
                            handler.process(jsMessage, jsMessageCallback)
                        }

                    // Process with feature handlers
                    handlers.getPlugins().map { it.getJsMessageHandler() }.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Exception is ${e.asLog()}" }
        }
    }

    override fun register(
        jsMessageCallback: JsMessageCallback,
        webView: WebView,
    ) {
        coroutineScope.launch {
            if (!webViewCompatContentScopeScripts.isEnabled()) return@launch

            runCatching {
                return@runCatching webViewCompatWrapper.addWebMessageListener(
                    webView,
                    JS_OBJECT_NAME,
                    allowedDomains,
                ) { _, message, _, _, _ ->
                    process(
                        message.data ?: "",
                        jsMessageCallback,
                    )
                }
            }.getOrElse { exception ->
                logcat(ERROR) { "Error adding WebMessageListener for contentScopeAdsjs: ${exception.asLog()}" }
            }
        }
    }

    override fun unregister(
        webView: WebView,
    ) {
        coroutineScope.launch {
            if (!webViewCompatContentScopeScripts.isEnabled()) return@launch
            runCatching {
                return@runCatching webViewCompatWrapper.removeWebMessageListener(webView, JS_OBJECT_NAME)
            }.getOrElse { exception ->
                logcat(ERROR) {
                    "Error removing WebMessageListener for contentScopeAdsjs: ${exception.asLog()}"
                }
            }
        }
    }
}
