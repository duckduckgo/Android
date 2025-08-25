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
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.browser.api.DuckDuckGoWebView
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.AdsjsContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.api.GlobalContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.AdsJsContentScopeScripts
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.AdsjsMessaging
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

private const val JS_OBJECT_NAME = "contentScopeAdsjs"

@ContributesBinding(ActivityScope::class)
@Named("AdsjsContentScopeScripts")
class AdsjsContentScopeMessaging @Inject constructor(
    private val handlers: PluginPoint<AdsjsContentScopeJsMessageHandlersPlugin>,
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
    private val adsJsContentScopeScripts: AdsJsContentScopeScripts,
    private val dispatcherProvider: DispatcherProvider,
) : AdsjsMessaging {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView

    override val context: String = "contentScopeScripts"
    override val allowedDomains: Set<String> = setOf("*")

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

    override suspend fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        if (withContext(dispatcherProvider.io()) { !adsJsContentScopeScripts.isEnabled() }) return
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView

        runCatching {
            (webView as? DuckDuckGoWebView)?.let {
                return@runCatching it.safeAddWebMessageListener(
                    JS_OBJECT_NAME,
                    allowedDomains,
                ) { _, message, _, _, replyProxy ->
                    process(
                        message.data ?: "",
                        jsMessageCallback,
                    )
                }
            } ?: false
        }.getOrElse { exception ->
            logcat(ERROR) { "Error adding WebMessageListener for contentScopeAdsjs: ${exception.asLog()}" }
            false
        }
    }

    override suspend fun unregister(webView: WebView) {
        if (!adsJsContentScopeScripts.isEnabled()) return
        withContext(dispatcherProvider.main()) {
            runCatching {
                return@runCatching (webView as? DuckDuckGoWebView)
                    ?.safeRemoveWebMessageListener(JS_OBJECT_NAME)
            }.getOrElse { exception ->
                logcat(ERROR) {
                    "Error removing WebMessageListener for contentScopeAdsjs: ${exception.asLog()}"
                }
            }
        }
    }
}
