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

interface NewJsMessaging {

    /**
     * Method to register the JS interface to the webView instance
     */
    fun register(webView: WebView, jsMessageCallback: JsMessageCallback?)

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
    val allowedDomains: Set<String>
}

interface NewJsMessageHandler {
    /**
     * This method processes a [JsMessage] and can return a JsRequestResponse to reply to the message if needed
     * @return `JsRequestResponse` or `null`
     */
    fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?)

    /**
     * Name of the feature
     */
    val featureName: String

    /**
     * List of the methods the handler can handle
     */
    val methods: List<String>
}
