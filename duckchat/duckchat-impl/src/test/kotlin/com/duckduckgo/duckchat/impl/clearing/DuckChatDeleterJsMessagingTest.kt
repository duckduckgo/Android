/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.clearing

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.clearing.DuckChatDeleterJsMessaging.Companion.JS_INTERFACE_NAME
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DuckChatDeleterJsMessagingTest {

    private val jsMessageHelper: JsMessageHelper = mock()
    private val webView: WebView = mock()
    private val jsMessageCallback: JsMessageCallback = mock()
    private val mockDuckAiHostProvider: DuckAiHostProvider = mock()

    private lateinit var messaging: DuckChatDeleterJsMessaging

    @Before
    fun setup() {
        messaging = DuckChatDeleterJsMessaging(
            jsMessageHelper = jsMessageHelper,
            duckAiHostProvider = mockDuckAiHostProvider,
        )
        messaging.register(webView, jsMessageCallback)
    }

    @Test
    fun `when register called then javascript interface is added to webview`() {
        val newWebView: WebView = mock()
        val newMessaging = DuckChatDeleterJsMessaging(jsMessageHelper, mockDuckAiHostProvider)

        newMessaging.register(newWebView, jsMessageCallback)

        verify(newWebView).addJavascriptInterface(newMessaging, JS_INTERFACE_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when register called with null callback then throws`() {
        val newMessaging = DuckChatDeleterJsMessaging(jsMessageHelper, mockDuckAiHostProvider)
        newMessaging.register(webView, null)
    }

    @Test
    fun `when process called with valid duckAiClearDataReady message then callback is invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "duckAiDataClearing",
                "method": "duckAiClearDataReady",
                "id": "123",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback).process(
            eq("duckAiDataClearing"),
            eq("duckAiClearDataReady"),
            eq("123"),
            any(),
        )
    }

    @Test
    fun `when process called with valid duckAiClearDataCompleted message then callback is invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "duckAiDataClearing",
                "method": "duckAiClearDataCompleted",
                "id": "456",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback).process(
            eq("duckAiDataClearing"),
            eq("duckAiClearDataCompleted"),
            eq("456"),
            any(),
        )
    }

    @Test
    fun `when process called with valid duckAiClearDataFailed message then callback is invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "duckAiDataClearing",
                "method": "duckAiClearDataFailed",
                "id": "789",
                "params": {"error": "storage error"}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback).process(
            eq("duckAiDataClearing"),
            eq("duckAiClearDataFailed"),
            eq("789"),
            any(),
        )
    }

    @Test
    fun `when process called with wrong secret then callback is not invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "duckAiDataClearing",
                "method": "duckAiClearDataReady",
                "id": "123",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, "wrongsecret")

        verify(jsMessageCallback, never()).process(any(), any(), any(), any())
    }

    @Test
    fun `when process called with wrong context then callback is not invoked`() {
        val messageJson = """
            {
                "context": "wrongContext",
                "featureName": "duckAiDataClearing",
                "method": "duckAiClearDataReady",
                "id": "123",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback, never()).process(any(), any(), any(), any())
    }

    @Test
    fun `when process called with unhandled method then callback is not invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "duckAiDataClearing",
                "method": "unknownMethod",
                "id": "123",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback, never()).process(any(), any(), any(), any())
    }

    @Test
    fun `when process called with wrong feature name then callback is not invoked`() {
        val messageJson = """
            {
                "context": "contentScopeScripts",
                "featureName": "wrongFeature",
                "method": "duckAiClearDataReady",
                "id": "123",
                "params": {}
            }
        """.trimIndent()

        messaging.process(messageJson, messaging.secret)

        verify(jsMessageCallback, never()).process(any(), any(), any(), any())
    }

    @Test
    fun `when process called with invalid json then no crash and callback is not invoked`() {
        messaging.process("not valid json", messaging.secret)

        verify(jsMessageCallback, never()).process(any(), any(), any(), any())
    }

    @Test
    fun `when sendSubscriptionEvent called then delegates to jsMessageHelper`() {
        val eventData = SubscriptionEventData(
            featureName = "duckAiDataClearing",
            subscriptionName = "duckAiClearData",
            params = JSONObject().apply { put("chatId", "abc-123") },
        )

        messaging.sendSubscriptionEvent(eventData)

        verify(jsMessageHelper).sendSubscriptionEvent(any(), eq(messaging.callbackName), eq(messaging.secret), eq(webView))
    }
}
