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

package com.duckduckgo.pir.internal.scripts

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.internal.brokers.JSONObjectAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import logcat.logcat

class PirMessagingInterface @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
) : JsMessaging {
    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).add(JSONObjectAdapter()).build() }
    private val handlers = listOf(
        BrokerProtectionMessageHandler(),
    )
    private lateinit var jsMessageCallback: JsMessageCallback
    private lateinit var webView: WebView

    override fun onResponse(response: JsCallbackData) {
        logcat { "PIR-CSS: onResponse $response" }
        val jsResponse = JsRequestResponse.Success(
            context = context,
            featureName = response.featureName,
            method = response.method,
            id = response.id,
            result = response.params,
        )

        jsMessageHelper.sendJsResponse(jsResponse, callbackName, secret, webView)
    }

    override fun register(
        webView: WebView,
        jsMessageCallback: JsMessageCallback?,
    ) {
        if (jsMessageCallback == null) throw Exception("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, PIRScriptConstants.SCRIPT_FEATURE_NAME)
    }

    @JavascriptInterface
    override fun process(
        message: String,
        secret: String,
    ) {
        logcat { "PIR-CSS: process $message secret $secret" }
        try {
            val adapter = moshi.adapter(JsMessage::class.java)
            val jsMessage = adapter.fromJson(message)

            jsMessage?.let {
                logcat { jsMessage.toString() }
                if (this.secret == secret && context == jsMessage.context) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, secret, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        logcat { "PIR-CSS: sendSubscriptionEvent $subscriptionEventData" }
        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )

        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
    }

    override val context: String = PIRScriptConstants.SCRIPT_CONTEXT_NAME
    override val callbackName: String = "messageCallback"
    override val secret: String = "messageSecret"
    override val allowedDomains: List<String> = emptyList()

    inner class BrokerProtectionMessageHandler() : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            secret: String,
            jsMessageCallback: JsMessageCallback?,
        ) {
            logcat { "PIR-CSS: BrokerProtectionMessageHandler: process $jsMessage" }
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = PIRScriptConstants.SCRIPT_FEATURE_NAME
        override val methods: List<String> = listOf(
            PIRScriptConstants.RECEIVED_METHOD_NAME_COMPLETED,
            PIRScriptConstants.RECEIVED_METHOD_NAME_ERROR,
        )
    }
}
