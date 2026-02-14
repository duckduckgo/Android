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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader

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
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import logcat.logcat
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

/**
 * JsMessaging for the headless chat suggestions WebView.
 * Handles the @JavascriptInterface bridge for receiving messages from the injected JS.
 */
class ChatSuggestionsJsMessaging @Inject constructor(
    private val jsMessageHelper: JsMessageHelper,
) : JsMessaging {

    override val secret: String = "duckduckgo-android-messaging-secret"
    override val callbackName: String = "messageCallback"

    private val moshi by lazy { Moshi.Builder().add(JSONObjectAdapter()).build() }
    private val handlers = listOf(DuckAiChatHistoryMessageHandler())
    private lateinit var jsMessageCallback: JsMessageCallback
    private lateinit var webView: WebView

    override val context: String = "contentScopeScripts"
    override val allowedDomains: List<String> = emptyList()

    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        if (jsMessageCallback == null) throw IllegalArgumentException("Callback cannot be null")
        this.webView = webView
        this.jsMessageCallback = jsMessageCallback
        this.webView.addJavascriptInterface(this, JS_INTERFACE_NAME)
    }

    @JavascriptInterface
    override fun process(message: String, secret: String) {
        try {
            val jsMessage = moshi.adapter(JsMessage::class.java).fromJson(message)
            jsMessage?.let {
                if (this.secret == secret && context == jsMessage.context) {
                    handlers.firstOrNull {
                        it.methods.contains(jsMessage.method) && it.featureName == jsMessage.featureName
                    }?.process(jsMessage, this, jsMessageCallback)
                }
            }
        } catch (e: Exception) {
            logcat { "Exception is ${e.message}" }
        }
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

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        val subscriptionEvent = SubscriptionEvent(
            context,
            subscriptionEventData.featureName,
            subscriptionEventData.subscriptionName,
            subscriptionEventData.params,
        )
        if (::webView.isInitialized) {
            jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, callbackName, secret, webView)
        }
    }

    class DuckAiChatHistoryMessageHandler : JsMessageHandler {
        override fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?) {
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = "duckAiChatHistory"
        override val methods: List<String> = listOf("duckAiChatsResult")
    }

    companion object {
        const val JS_INTERFACE_NAME = "chatSuggestionsInterface"
    }
}

internal class JSONObjectAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): JSONObject? {
        return (reader.readJsonValue() as? Map<*, *>)?.let { data ->
            try {
                JSONObject(data)
            } catch (_: JSONException) {
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: JSONObject?) {
        value?.let { writer.run { value(Buffer().writeUtf8(value.toString())) } }
    }
}
