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

package com.duckduckgo.js.messaging.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.ProcessResult.SendResponse
import com.duckduckgo.js.messaging.api.ProcessResult.SendToConsumer
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebMessagingPluginDelegate
import com.duckduckgo.js.messaging.api.WebMessagingPluginStrategy
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import org.json.JSONObject

@ContributesBinding(FragmentScope::class)
class RealWebMessagingDelegate @Inject constructor(
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : WebMessagingPluginDelegate {
    override fun createPlugin(strategy: WebMessagingPluginStrategy): WebMessagingPlugin {
        return object : WebMessagingPlugin {
            private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

            private var globalReplyProxy: JavaScriptReplyProxy? = null

            override val context: String
                get() = strategy.context

            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
            internal fun process(
                webView: WebView,
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
                            if (jsMessage.featureName == "messaging" && jsMessage.method == "initialPing") {
                                globalReplyProxy = replyProxy
                            }

                            // Process global handlers first (always processed regardless of feature handlers)
                            strategy.getGlobalMessageHandler()
                                .filter { it.method == jsMessage.method }
                                .forEach { handler ->
                                    handler.process(jsMessage)?.let { processResult ->
                                        when (processResult) {
                                            is SendToConsumer -> {
                                                sendToConsumer(webView, jsMessageCallback, jsMessage, replyProxy)
                                            }
                                            is SendResponse -> {
                                                webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                                                    onResponse(webView, jsMessage, replyProxy)
                                                }
                                            }
                                        }
                                    }
                                }

                            // Process with feature handlers
                            strategy.getMessageHandlers().firstOrNull {
                                it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                            }?.process(jsMessage)?.let { processResult ->
                                when (processResult) {
                                    is SendToConsumer -> {
                                        sendToConsumer(webView, jsMessageCallback, jsMessage, replyProxy)
                                    }
                                    is SendResponse -> {
                                        webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                                            onResponse(webView, jsMessage, replyProxy)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logcat(ERROR) { "Exception is ${e.asLog()}" }
                }
            }

            private suspend fun onResponse(
                webView: WebView,
                jsMessage: JsMessage,
                replyProxy: JavaScriptReplyProxy,
            ) {
                val callbackData = JsCallbackData(
                    id = jsMessage.id ?: "",
                    params = jsMessage.params,
                    featureName = jsMessage.featureName,
                    method = jsMessage.method,
                )
                onResponse(webView, callbackData, replyProxy)
            }

            private fun sendToConsumer(
                webView: WebView,
                jsMessageCallback: WebViewCompatMessageCallback,
                jsMessage: JsMessage,
                replyProxy: JavaScriptReplyProxy,
            ) {
                jsMessageCallback.process(
                    context = context,
                    featureName = jsMessage.featureName,
                    method = jsMessage.method,
                    id = jsMessage.id ?: "",
                    data = jsMessage.params,
                    onResponse = { response: JSONObject ->
                        val callbackData = JsCallbackData(
                            id = jsMessage.id ?: "",
                            params = response,
                            featureName = jsMessage.featureName,
                            method = jsMessage.method,
                        )
                        onResponse(webView, callbackData, replyProxy)
                    },
                )
            }

            override suspend fun register(
                jsMessageCallback: WebViewCompatMessageCallback,
                webView: WebView,
            ) {
                if (!strategy.canHandleMessaging()) {
                return
            }

                runCatching {
                    return@runCatching webViewCompatWrapper.addWebMessageListener(
                        webView,
                        strategy.objectName,
                        strategy.allowedDomains,
                    ) { _, message, _, _, replyProxy ->
                        process(
                            webView,
                            message.data ?: "",
                            jsMessageCallback,
                            replyProxy,
                        )
                    }
                }.getOrElse { exception ->
                    logcat(ERROR) { "Error adding WebMessageListener for ${strategy.objectName}: ${exception.asLog()}" }
                }

            }

            override suspend fun unregister(
                webView: WebView,
            ) {
                if (!strategy.canHandleMessaging()) return
                runCatching {
                    return@runCatching webViewCompatWrapper.removeWebMessageListener(webView, strategy.objectName)
                }.getOrElse { exception ->
                    logcat(ERROR) {
                        "Error removing WebMessageListener for ${strategy.objectName}: ${exception.asLog()}"
                    }
                }
            }

            @SuppressLint("RequiresFeature")
            private suspend fun onResponse(
                webView: WebView,
                response: JsCallbackData,
                replyProxy: JavaScriptReplyProxy,
            ) {
                runCatching {
                    val responseWithId = JSONObject().apply {
                        put("id", response.id)
                        put("result", response.params)
                        put("featureName", response.featureName)
                        put("context", context)
                    }
                    webViewCompatWrapper.postMessage(webView, replyProxy, responseWithId.toString())
                }
            }

            @SuppressLint("RequiresFeature")
            override suspend fun postMessage(webView: WebView, subscriptionEventData: SubscriptionEventData) {
                runCatching {
                    if (!strategy.canHandleMessaging()) {
                        return
                    }

                    val subscriptionEvent = SubscriptionEvent(
                        context = context,
                        featureName = subscriptionEventData.featureName,
                        subscriptionName = subscriptionEventData.subscriptionName,
                        params = subscriptionEventData.params,
                    ).let {
                        moshi.adapter(SubscriptionEvent::class.java).toJson(it)
                    }

                    webViewCompatWrapper.postMessage(webView, globalReplyProxy, subscriptionEvent)
                }
            }
        }
    }
}
