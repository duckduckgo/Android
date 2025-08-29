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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants

/**
 * Fake implementation of [JsMessaging] for testing purposes.
 * Captures responses and events for verification in tests.
 */
class FakeJsMessaging : JsMessaging {

    private val _responses = mutableListOf<JsCallbackData>()
    private val _subscriptionEvents = mutableListOf<SubscriptionEventData>()
    private val _processedMessages = mutableListOf<Pair<String, String>>()

    /** List of responses sent via [onResponse] */
    val responses: List<JsCallbackData> = _responses

    /** List of subscription events sent via [sendSubscriptionEvent] */
    val subscriptionEvents: List<SubscriptionEventData> = _subscriptionEvents

    /** List of messages processed via [process] as (message, secret) pairs */
    val processedMessages: List<Pair<String, String>> = _processedMessages

    override val context: String = PirDashboardWebConstants.SCRIPT_CONTEXT_NAME
    override val callbackName: String = PirDashboardWebConstants.MESSAGE_CALLBACK
    override val secret: String = "test-secret"
    override val allowedDomains: List<String> = listOf("test.com")

    override fun onResponse(response: JsCallbackData) {
        _responses.add(response)
    }

    override fun register(webView: WebView, jsMessageCallback: JsMessageCallback?) {
        // No-op for testing
    }

    override fun process(message: String, secret: String) {
        _processedMessages.add(message to secret)
    }

    override fun sendSubscriptionEvent(subscriptionEventData: SubscriptionEventData) {
        _subscriptionEvents.add(subscriptionEventData)
    }

    /**
     * Clears all captured data. Useful for test setup.
     */
    fun reset() {
        _responses.clear()
        _subscriptionEvents.clear()
        _processedMessages.clear()
    }

    fun getLastResponse(): JsCallbackData? = _responses.lastOrNull()

    fun getResponseCount(): Int = _responses.size

    fun getLastSubscriptionEvent(): SubscriptionEventData? = _subscriptionEvents.lastOrNull()

    fun getLastResponseParams() = getLastResponse()?.params
}
