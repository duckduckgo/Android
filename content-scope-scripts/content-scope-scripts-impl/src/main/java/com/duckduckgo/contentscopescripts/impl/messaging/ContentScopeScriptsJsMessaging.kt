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

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.impl.CoreContentScopeScripts
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@ContributesBinding(FragmentScope::class)
@Named("ContentScopeScripts")
class ContentScopeScriptsJsMessaging @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
    private val dispatcherProvider: DispatcherProvider,
    private val coreContentScopeScripts: CoreContentScopeScripts,
) : JsMessaging {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    private lateinit var webView: WebView
    private lateinit var jsMessageCallback: JsMessageCallback

    override val context: String = "contentScopeScripts"
    override val callbackName: String = coreContentScopeScripts.callbackName
    override val secret: String = coreContentScopeScripts.secret
    override val allowedDomains: List<String> = emptyList()

    private val handlers: List<JsMessageHandler> = listOf(ContentScopeHandler(), DuckPlayerHandler())

    @JavascriptInterface
    override fun process(message: String, secret: String) {
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)
            val domain = runBlocking(dispatcherProvider.main()) {
                webView.url?.toUri()?.host
            }
            jsMessage?.let {
                if (this.secret == secret && context == jsMessage.context && (allowedDomains.isEmpty() || allowedDomains.contains(domain))) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, secret, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Exception is ${e.asLog()}" }
        }
    }

    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, coreContentScopeScripts.javascriptInterface)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )
        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
    }

    override fun onResponse(response: JsCallbackData) {
        val jsResponse = JsRequestResponse.Success(
            context = context,
            featureName = response.featureName,
            method = response.method,
            id = response.id,
            result = response.params,
        )
        jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, webView)
    }

    inner class ContentScopeHandler : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            if (jsMessage.id == null) return
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "webCompat"
        override val methods: List<String> = listOf("webShare", "permissionsQuery", "screenLock", "screenUnlock")
    }

    inner class DuckPlayerHandler : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            // TODO (cbarreiro): Add again when https://app.asana.com/0/0/1207602010403610/f is fixed
            // if (jsMessage.id == null) return
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "duckPlayer"
        override val methods: List<String> = listOf(
            "getUserValues",
            "sendDuckPlayerPixel",
            "setUserValues",
            "openDuckPlayer",
            "openInfo",
            "initialSetup",
            "reportPageException",
            "reportInitException",
        )
    }
}
