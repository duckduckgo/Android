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

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import org.json.JSONObject

/**
 * Plugin interface for global message handlers that should always be processed
 * regardless of whether a specific feature handler matches the message.
 * * Examples: addDebugFlag.
 */
interface GlobalContentScopeJsMessageHandlersPlugin {

    /**
     * @return a [GlobalJsMessageHandler] that will be used to handle global messages
     */
    fun getGlobalJsMessageHandler(): GlobalJsMessageHandler
}

/**
 * Handler for global messages that should be processed for all features.
 */
interface GlobalJsMessageHandler {

    /**
     * Processes a global message received by the WebView.
     *
     * This method is responsible for handling a [JsMessage] and optionally
     * invoking a callback so consumers can also process the message if needed.
     *
     * @param jsMessage The JavaScript message to be processed.
     * @param jsMessageCallback An optional callback to handle the result of the message processing.
     */
    fun process(
        jsMessage: JsMessage,
        jsMessageCallback: WebViewCompatMessageCallback,
        onResponse: (JSONObject) -> Unit,
    )

    /**
     * Method this handler can process.
     */
    val method: String
}
