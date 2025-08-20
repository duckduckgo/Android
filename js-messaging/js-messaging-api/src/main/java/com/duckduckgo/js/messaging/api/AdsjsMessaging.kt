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

import android.webkit.WebView
import org.json.JSONObject

interface AdsjsMessaging {

    /**
     * Method to register the JS interface to the webView instance
     */
    suspend fun register(
        webView: WebView,
        jsMessageCallback: AdsjsJsMessageCallback?,
    )
    suspend fun unregister(webView: WebView)

    fun postMessage(subscriptionEventData: SubscriptionEventData)

    /**
     * Context name
     */
    val context: String

    /**
     * List of domains where the interface can be added
     */
    val allowedDomains: Set<String>
}

abstract class AdsjsJsMessageCallback {
    abstract fun process(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        onResponse: (params: JSONObject) -> Unit,
    )
}

interface AdsjsMessageHandler {
    /**
     * This method processes a [JsMessage]
     */
    fun process(
        jsMessage: JsMessage,
        jsMessageCallback: AdsjsJsMessageCallback?,
        onResponse: (JSONObject) -> Unit,
    )

    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * List of the methods the handler can handle
     */
    val methods: List<String>
}
