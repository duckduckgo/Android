/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.GlobalContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.api.NewContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.NewJsMessaging
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@ContributesBinding(ActivityScope::class)
@Named("AdsJsContentScopeScripts")
class AdsJsContentScopeMessaging @Inject constructor(
    private val handlers: PluginPoint<NewContentScopeJsMessageHandlersPlugin>,
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
) : NewJsMessaging {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView

    override val context: String = "contentScopeScripts"
    override val allowedDomains: Set<String> = setOf("*")

    private fun process(
        message: String,
        jsMessageCallback: JsMessageCallback,
        replyProxy: JavaScriptReplyProxy,
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
                            handler.process(jsMessage, jsMessageCallback, replyProxy)
                        }

                    // Process with feature handlers
                    handlers.getPlugins().map { it.getJsMessageHandler() }.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, jsMessageCallback, replyProxy)
                }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Exception is ${e.asLog()}" }
        }
    }

    // TODO: A/B this, don't register if the feature is not enabled
    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView

        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    webView,
                    "contentScopeAdsjs",
                    allowedDomains,
                ) { _, message, _, _, replyProxy ->
                    process(
                        message.data ?: "",
                        jsMessageCallback,
                        replyProxy,
                    )
                }
                true
            } else {
                false
            }
        }.getOrElse { exception ->
            logcat(ERROR) { "Error adding WebMessageListener for contentScopeAdsjs: ${exception.asLog()}" }
            false
        }
    }
}
