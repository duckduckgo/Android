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
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.ProcessResult.SendResponse
import com.duckduckgo.js.messaging.api.ProcessResult.SendToConsumer
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named

private const val JS_OBJECT_NAME = "contentScopeAdsjs"

@Named("contentScopeScripts")
@SingleInstanceIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class)
@ContributesMultibinding(scope = FragmentScope::class, ignoreQualifier = true)
class ContentScopeScriptsWebMessagingPlugin @Inject constructor(
    private val handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin>,
    private val legacyHandlers: PluginPoint<ContentScopeJsMessageHandlersPlugin>,
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : WebMessagingPlugin {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override val context: String = "contentScopeScripts"
    private val allowedDomains: Set<String> = setOf("*")

    private var globalReplyProxy: JavaScriptReplyProxy? = null

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
                    maybeAddNativeData(webView, jsMessage)

                    // Process global handlers first (always processed regardless of feature handlers)
                    globalHandlers
                        .getPlugins()
                        .map { it.getGlobalJsMessageHandler() }
                        .filter { it.method == jsMessage.method }
                        .forEach { handler ->
                            handler.process(jsMessage)?.let { processResult ->
                                when (processResult) {
                                    is SendToConsumer -> {
                                        sendToConsumer(webView, jsMessageCallback, jsMessage, replyProxy)
                                    }
                                    is SendResponse -> {
                                        webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                                            onResponse(webView, jsMessage, processResult.response, replyProxy)
                                        }
                                    }
                                }
                            }
                        }

                    if (!processWebViewCompatHandler(webView, jsMessage, jsMessageCallback, replyProxy)) {
                        processLegacyHandler(webView, jsMessage, jsMessageCallback, replyProxy)
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
        params: JSONObject,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val callbackData =
            JsCallbackData(
                id = jsMessage.id ?: "",
                params = params,
                featureName = jsMessage.featureName,
                method = jsMessage.method,
            )
        onResponse(webView, callbackData, replyProxy)
    }

    private fun processWebViewCompatHandler(
        webView: WebView,
        jsMessage: JsMessage,
        jsMessageCallback: WebViewCompatMessageCallback,
        replyProxy: JavaScriptReplyProxy,
    ): Boolean {
        val handler =
            handlers
                .getPlugins()
                .map { it.getJsMessageHandler() }
                .firstOrNull {
                    it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                } ?: return false

        handler.process(jsMessage)?.let { processResult ->
            when (processResult) {
                is SendToConsumer -> {
                    sendToConsumer(webView, jsMessageCallback, jsMessage, replyProxy)
                }
                is SendResponse -> {
                    webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        onResponse(webView, jsMessage, processResult.response, replyProxy)
                    }
                }
            }
        }
        return true
    }

    private fun processLegacyHandler(
        webView: WebView,
        jsMessage: JsMessage,
        jsMessageCallback: WebViewCompatMessageCallback,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val domain = webView.url?.toUri()?.host
        legacyHandlers
            .getPlugins()
            .map { it.getJsMessageHandler() }
            .firstOrNull {
                it.methods.contains(jsMessage.method) &&
                    it.featureName == jsMessage.featureName &&
                    isUrlAllowed(it.allowedDomains, domain)
            }?.process(
                jsMessage,
                legacyJsMessaging(webView, replyProxy),
                legacyJsMessageCallback(webView, jsMessageCallback, replyProxy),
            )
    }

    private fun legacyJsMessageCallback(
        webView: WebView,
        jsMessageCallback: WebViewCompatMessageCallback,
        replyProxy: JavaScriptReplyProxy,
    ) = object : JsMessageCallback() {
        override fun process(
            featureName: String,
            method: String,
            id: String?,
            data: JSONObject?,
        ) {
            jsMessageCallback.process(
                context = context,
                featureName = featureName,
                method = method,
                id = id ?: "",
                data = data,
                onResponse = { response ->
                    onResponse(
                        webView,
                        JsCallbackData(
                            id = id ?: "",
                            params = response,
                            featureName = featureName,
                            method = method,
                        ),
                        replyProxy,
                    )
                },
            )
        }
    }

    private fun legacyJsMessaging(
        webView: WebView,
        replyProxy: JavaScriptReplyProxy,
    ) = object : JsMessaging {
        override fun onResponse(response: JsCallbackData) {
            webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                onResponse(webView, response, replyProxy)
            }
        }

        override fun register(
            webView: WebView,
            jsMessageCallback: JsMessageCallback?,
        ) = Unit

        override fun process(
            message: String,
            secret: String,
        ) = Unit

        override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
            webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                postMessage(webView, subscriptionEventData)
            }
        }

        override val context: String = this@ContentScopeScriptsWebMessagingPlugin.context
        override val callbackName: String = ""
        override val secret: String = ""
        override val allowedDomains: List<String> = emptyList()
    }

    private fun maybeAddNativeData(
        webView: WebView,
        jsMessage: JsMessage,
    ) {
        if (jsMessage.featureName == "webEvents" && jsMessage.method == "webEvent") {
            val nativeData = JSONObject()
            nativeData.put("webViewId", System.identityHashCode(webView).toString())
            jsMessage.params.put("nativeData", nativeData)
        }
    }

    private fun isUrlAllowed(
        allowedDomains: List<String>,
        url: String?,
    ): Boolean {
        if (allowedDomains.isEmpty()) return true
        val host = url ?: return false
        val eTld = host.toTldPlusOne()
        return allowedDomains.contains(host) || (eTld != null && allowedDomains.contains(eTld))
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
                val callbackData =
                    JsCallbackData(
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
        if (!webViewCompatContentScopeScripts.isWebMessagingEnabled()) {
            return
        }

        runCatching {
            return@runCatching webViewCompatWrapper.addWebMessageListener(
                webView,
                JS_OBJECT_NAME,
                allowedDomains,
            ) { _, message, _, _, replyProxy ->
                process(
                    webView,
                    message.data ?: "",
                    jsMessageCallback,
                    replyProxy,
                )
            }
        }.getOrElse { exception ->
            logcat(ERROR) { "Error adding WebMessageListener for contentScopeAdsjs: ${exception.asLog()}" }
        }
    }

    override suspend fun unregister(webView: WebView) {
        if (!webViewCompatContentScopeScripts.isWebMessagingEnabled()) return
        runCatching {
            return@runCatching webViewCompatWrapper.removeWebMessageListener(webView, JS_OBJECT_NAME)
        }.getOrElse { exception ->
            logcat(ERROR) {
                "Error removing WebMessageListener for contentScopeAdsjs: ${exception.asLog()}"
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
            val responseWithId =
                JSONObject().apply {
                    put("id", response.id)
                    put("result", response.params)
                    put("featureName", response.featureName)
                    put("context", context)
                }
            webViewCompatWrapper.postMessage(webView, replyProxy, responseWithId.toString())
        }
    }

    @SuppressLint("RequiresFeature")
    override suspend fun postMessage(
        webView: WebView,
        subscriptionEventData: SubscriptionEventData,
    ) {
        runCatching {
            if (!webViewCompatContentScopeScripts.isWebMessagingEnabled()) {
                return
            }

            val subscriptionEvent =
                SubscriptionEvent(
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
