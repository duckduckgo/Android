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

package com.duckduckgo.duckchat.impl.messaging.fakes

import android.webkit.WebView
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData

/**
 * Fake implementation of [JsMessaging] for testing purposes.
 * Captures responses and events for verification in tests.
 */
class FakeJsMessaging : JsMessaging {

    private val _responses = mutableListOf<JsCallbackData>()
    private val _subscriptionEvents = mutableListOf<SubscriptionEventData>()
    private val _processedMessages = mutableListOf<Pair<String, String>>()

    override val context: String = "serpSettings"
    override val callbackName: String = "messageCallback"
    override val secret: String = "test-secret"
    override val allowedDomains: List<String> = listOf("duckduckgo.com")

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

    fun getLastResponse(): JsCallbackData? = _responses.lastOrNull()

    fun getResponseCount(): Int = _responses.size
}
