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

package com.duckduckgo.js.messaging.api

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

interface JsMessaging {

    /**
     * Method to send a response back to the JS code. Takes a [JsCallbackData] and uses it to create the response and send it.
     */
    fun onResponse(response: JsCallbackData)

    /**
     * Method to register the JS interface to the webView instance
     */
    fun register(webView: WebView, jsMessageCallback: JsMessageCallback?)

    /**
     * JS Interface to process a message
     */
    @JavascriptInterface
    fun process(message: String, secret: String)

    /**
     * Method to send a subscription event
     */
    fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData)

    /**
     * Context name
     */
    val context: String

    /**
     * Name of the JS callback
     */
    val callbackName: String

    /**
     * Secret to use in the JS code
     */
    val secret: String

    /**
     * List of domains where the interface can be added
     */
    val allowedDomains: List<String>
}

abstract class JsMessageCallback {
    abstract fun process(featureName: String, method: String, id: String?, data: JSONObject?)
}

/**
 * Handler to be used in all plugins
 */
interface JsMessageHelper {
    /**
     * Method to process all messages
     */
    fun sendJsResponse(jsRequestResponse: JsRequestResponse, callbackName: String, secret: String, webView: WebView)

    /**
     * Method to send a subscription event.
     */
    fun sendSubscriptionEvent(subscriptionEvent: SubscriptionEvent, callbackName: String, secret: String, webView: WebView)
}

interface JsMessageHandler {
    /**
     * This method processes a [JsMessage] and can return a JsRequestResponse to reply to the message if needed
     * @return `JsRequestResponse` or `null`
     */
    fun process(jsMessage: JsMessage, jsMessaging: JsMessaging, jsMessageCallback: JsMessageCallback?)

    /**
     * List of domains where we can process the message
     */
    val allowedDomains: List<String>

    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * List of the methods the handler can handle
     */
    val methods: List<String>
}

data class JsMessage(
    val context: String,
    val featureName: String,
    val method: String,
    val params: JSONObject,
    val id: String?,
)

data class JsCallbackData(
    val params: JSONObject,
    val featureName: String,
    val method: String,
    val id: String,
)

sealed class JsRequestResponse {
    data class Success(
        val context: String,
        val featureName: String,
        val method: String,
        val id: String,
        val result: JSONObject,
    ) : JsRequestResponse()

    data class Error(
        val context: String,
        val featureName: String,
        val method: String,
        val id: String,
        val error: String,
    ) : JsRequestResponse()
}

data class SubscriptionEventData(val featureName: String, val subscriptionName: String, val params: JSONObject)
data class SubscriptionEvent(val context: String, val featureName: String, val subscriptionName: String, val params: JSONObject)
