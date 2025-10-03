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

interface WebMessaging {
    /**
     * Registers the given [jsMessageCallback] to handle messages from the provided [webView].
     * @param jsMessageCallback the callback to handle incoming messages
     * @param webView the WebView to register the callback with
     * Notes:
     * - It's not recommended to unregister and then register again on the same WebView instance.
     */
    suspend fun register(
        jsMessageCallback: WebViewCompatMessageCallback,
        webView: WebView,
    )

    /**
     * Unregisters any previously registered message handlers from the given [webView].
     * Notes:
     * - This does not remove the JavaScript interface from the WebView, just the handlers.
     * - It's not required to call this when the WebView is being destroyed.
     * - It's not recommended to unregister and then register again on the same WebView instance.
     * @param webView the WebView to unregister the handlers from
     */
    suspend fun unregister(webView: WebView)

    /**
     * Posts a message to the given [webView] using the provided [subscriptionEventData].
     * @param webView the WebView to which the message should be posted
     * @param subscriptionEventData the data to be sent in the message
     */
    suspend fun postMessage(
        webView: WebView,
        subscriptionEventData: SubscriptionEventData,
    )

    /**
     * The context for this instance.
     * This can be used to differentiate between different messaging implementations.
     * @return context string
     */
    val context: String
}

interface WebMessagingDelegate {

    /**
     * Creates a [WebMessaging] implementation with the given [WebMessagingStrategy].
     * @param strategy the strategy to use for web messaging behavior
     * @return [WebMessaging] implementation
     */
    fun createPlugin(strategy: WebMessagingStrategy): WebMessaging
}

/**
 * Strategy interface for web messaging logic.
 * Allows different implementations to provide their own behavior.
 */
interface WebMessagingStrategy {
    val context: String
    val allowedDomains: Set<String>
    val objectName: String

    /**
     * Determines whether messaging can be handled (i.e. by checking feature flags).
     * @return true if messaging can be handled for this plugin, false otherwise
     */
    suspend fun canHandleMessaging(): Boolean

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
