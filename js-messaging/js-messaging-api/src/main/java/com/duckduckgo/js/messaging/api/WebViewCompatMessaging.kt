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

interface WebViewCompatMessageHandler {
    /**
     * Processes a JavaScript message received by the WebView using WebCompat APIs
     *
     * This method is responsible for handling a [JsMessage] and optionally
     * invoking a callback so consumers can also process the message if needed.
     *
     * @param jsMessage The JavaScript message to be processed.
     * @param jsMessageCallback An optional callback to handle the result of the message processing.
     */
    fun process(
        jsMessage: JsMessage,
        jsMessageCallback: JsMessageCallback?,
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
