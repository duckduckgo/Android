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
import androidx.annotation.MainThread
import org.json.JSONObject

/**
 * Handler to be used in all plugins
 */
interface JsMessageHandler {
    /**
     * Method to process all messages
     */
    fun process(message: String, context: String, secret: String, callback: String, webView: WebView)

    /**
     * Method to send a subscription event
     */
    @MainThread
    fun sendSubscriptionEvent(subscriptionEvent: SubscriptionEvent, callback: String, secret: String, webView: WebView)
}

interface JsMessageHandlerPlugin {
    /**
     * This method processes a [JsMessage] and can return a JsRequestResponse to reply to the message if needed
     * @return `JsRequestResponse` or `null`
     */
    fun process(jsMessage: JsMessage, secret: String, callback: String, webView: WebView): JsRequestResponse?

    /**
     * List of domains where we can process the message
     */
    val allowedDomains: List<String>

    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * Name of the method
     */
    val method: String
}

data class JsMessage(
    val context: String,
    val featureName: String,
    val method: String,
    val params: JSONObject,
    val id: String?,
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

data class SubscriptionEvent(val context: String, val featureName: String, val subscriptionName: String, val params: JSONObject?)

interface JsMessaging {

    /**
     * JS Interface to process a message
     */
    @JavascriptInterface
    fun process(message: String, secret: String)

    /**
     * Method to register the JS Interface
     */
    fun registerJsInterface()

    /**
     * Method to send a subscription event
     */
    fun sendSubscriptionEvent()

    /**
     * Context name
     */
    val context: String

    /**
     * Name of the callback
     */
    val callback: String

    /**
     * Secret to use in the JS code
     */
    val secret: String
}
