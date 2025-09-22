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

package com.duckduckgo.js.messaging.api

import android.webkit.WebView

interface WebMessagingPlugin {
    suspend fun register(
        jsMessageCallback: WebViewCompatMessageCallback,
        webView: WebView,
    )

    suspend fun unregister(webView: WebView)

    suspend fun postMessage(
        webView: WebView,
        subscriptionEventData: SubscriptionEventData,
    )

    val context: String
}

interface WebMessagingPluginDelegate {

    /**
     * Creates a [WebMessagingPlugin] implementation with the given [WebMessagingPluginStrategy].
     * @param strategy the strategy to use for web messaging behavior
     * @return [WebMessagingPlugin] implementation
     */
    fun createPlugin(strategy: WebMessagingPluginStrategy): WebMessagingPlugin
}

/**
 * Strategy interface for web messaging logic.
 * Allows different implementations to provide their own behavior.
 */
interface WebMessagingPluginStrategy {
    val context: String
    val allowedDomains: Set<String>
    val objectName: String

    /**
     * Determines whether messaging actions should proceed (i.e. by checking feature flags).
     * @return true if messaging is allowed, false otherwise
     */
    suspend fun isEnabled(): Boolean

    /**
     * Provides the list of message handlers to process incoming messages.
     * @return list of [WebViewCompatMessageHandler] implementations
     */
    fun getMessageHandlers(): List<WebViewCompatMessageHandler>

    /**
     * Provides the list of global message handlers that should always be processed
     * regardless of whether a specific feature handler matches the message.
     * @return list of [GlobalJsMessageHandler] implementations
     */
    fun getGlobalMessageHandler(): List<GlobalJsMessageHandler>
}
