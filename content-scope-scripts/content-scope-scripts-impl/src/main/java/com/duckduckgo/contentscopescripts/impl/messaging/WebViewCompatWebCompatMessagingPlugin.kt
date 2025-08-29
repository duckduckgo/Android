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

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.WebMessagingPlugin
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler.ProcessResult.SendResponse
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler.ProcessResult.SendToConsumer
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import org.json.JSONObject

private const val JS_OBJECT_NAME = "contentScopeAdsjs"

@ContributesMultibinding(ActivityScope::class)
class WebViewCompatWebCompatMessagingPlugin @Inject constructor(
    private val handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin>,
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val dispatcherProvider: DispatcherProvider,
) : WebMessagingPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private val context: String = "contentScopeScripts"
    private val allowedDomains: Set<String> = setOf("*")

    private var globalReplyProxy: JavaScriptReplyProxy? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun process(
        message: String,
        jsMessageCallback: WebViewCompatMessageCallback,
        replyProxy: JavaScriptReplyProxy,
    ) {
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)

            jsMessage?.let {
                if (context == jsMessage.context) {
                    // Setup reply proxy so we can send subscription events
                    if (jsMessage.featureName == "messaging" || jsMessage.method == "initialPing") {
                        logcat("Cris") { "initialPing" }
                        globalReplyProxy = replyProxy
                    }

                    // Process global handlers first (always processed regardless of feature handlers)
                    globalHandlers.getPlugins()
                        .map { it.getGlobalJsMessageHandler() }
                        .filter { it.method == jsMessage.method }
                        .forEach { handler ->
                            handler.process(jsMessage)?.let { processResult ->
                                when (processResult) {
                                    is SendToConsumer -> {
                                        sendToConsumer(jsMessageCallback, jsMessage, replyProxy)
                                    }
                                    is SendResponse -> {
                                        onResponse(jsMessage, replyProxy)
                                    }
                                }
                            }
                        }

                    // Process with feature handlers
                    handlers.getPlugins().map { it.getJsMessageHandler() }.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage)?.let { processResult ->
                        when (processResult) {
                            is SendToConsumer -> {
                                sendToConsumer(jsMessageCallback, jsMessage, replyProxy)
                            }
                            is SendResponse -> {
                                onResponse(jsMessage, replyProxy)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Exception is ${e.asLog()}" }
        }
    }

    private fun onResponse(
        jsMessage: JsMessage,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val callbackData = JsCallbackData(
            id = jsMessage.id ?: "",
            params = jsMessage.params,
            featureName = jsMessage.featureName,
            method = jsMessage.method,
        )
        onResponse(callbackData, replyProxy)
    }

    private fun sendToConsumer(
        jsMessageCallback: WebViewCompatMessageCallback,
        jsMessage: JsMessage,
        replyProxy: JavaScriptReplyProxy,
    ) {
        jsMessageCallback.process(
            jsMessage.featureName,
            jsMessage.method,
            jsMessage.id ?: "",
            jsMessage.params,
            { response: JSONObject ->
                val callbackData = JsCallbackData(
                    id = jsMessage.id ?: "",
                    params = response,
                    featureName = jsMessage.featureName,
                    method = jsMessage.method,
                )
                onResponse(callbackData, replyProxy)
            },
        )
    }

    override suspend fun register(
        jsMessageCallback: WebViewCompatMessageCallback?,
        registerer: suspend (objectName: String, allowedOriginRules: Set<String>, webMessageListener: WebMessageListener) -> Boolean,
    ) {
        if (withContext(dispatcherProvider.io()) { !webViewCompatContentScopeScripts.isEnabled() }) return
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")

        runCatching {
            return@runCatching registerer(
                JS_OBJECT_NAME,
                allowedDomains,
                WebMessageListener { _, message, _, _, replyProxy ->
                    process(
                        message.data ?: "",
                        jsMessageCallback,
                        replyProxy,
                    )
                },
            )
        }.getOrElse { exception ->
            logcat(ERROR) { "Error adding WebMessageListener for contentScopeAdsjs: ${exception.asLog()}" }
            false
        }
    }

    override suspend fun unregister(
        unregisterer: suspend (objectName: String) -> Boolean,
    ) {
        if (!webViewCompatContentScopeScripts.isEnabled()) return
        withContext(dispatcherProvider.main()) {
            runCatching {
                return@runCatching unregisterer(JS_OBJECT_NAME)
            }.getOrElse { exception ->
                logcat(ERROR) {
                    "Error removing WebMessageListener for contentScopeAdsjs: ${exception.asLog()}"
                }
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun onResponse(response: JsCallbackData, replyProxy: JavaScriptReplyProxy) {
        runCatching {
            val responseWithId = JSONObject().apply {
                put("id", response.id)
                put("result", response.params)
                put("featureName", response.featureName)
                put("context", context)
            }
            replyProxy.postMessage(responseWithId.toString())
        }
    }

    @SuppressLint("RequiresFeature")
    override fun postMessage(subscriptionEventData: SubscriptionEventData): Boolean {
        return runCatching {
            // TODO (cbarreiro) temporary, remove
            val newWebCompatApisEnabled = runBlocking {
                webViewCompatContentScopeScripts.isEnabled()
            }

            if (!newWebCompatApisEnabled) {
                return false
            }

            val subscriptionEvent = SubscriptionEvent(
                context = context,
                featureName = subscriptionEventData.featureName,
                subscriptionName = subscriptionEventData.subscriptionName,
                params = subscriptionEventData.params,
            ).let {
                moshi.adapter(SubscriptionEvent::class.java).toJson(it)
            }

            globalReplyProxy?.postMessage(subscriptionEvent)
            true
        }.getOrElse { false }
    }
}
