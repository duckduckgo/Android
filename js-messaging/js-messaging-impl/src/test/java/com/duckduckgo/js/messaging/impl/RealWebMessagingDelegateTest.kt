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

package com.duckduckgo.js.messaging.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.js.messaging.api.GlobalJsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.ProcessResult
import com.duckduckgo.js.messaging.api.ProcessResult.SendToConsumer
import com.duckduckgo.js.messaging.api.WebMessaging
import com.duckduckgo.js.messaging.api.WebMessagingStrategy
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RealWebMessagingDelegateTest {
    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()
    private val mockWebView: WebView = mock()
    private val mockReplyProxy: JavaScriptReplyProxy = mock()
    private val mockWebViewCompatMessageCallback: WebViewCompatMessageCallback = mock()

    private lateinit var testee: RealWebMessagingDelegate
    private lateinit var plugin: WebMessaging

    @Before
    fun setUp() = runTest {
        testee = RealWebMessagingDelegate(
            webViewCompatWrapper = mockWebViewCompatWrapper,
        )
    }

    @Test
    fun `when registering and feature enabled then register web message listener`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)

        plugin.register(mockWebViewCompatMessageCallback, mockWebView)

        verify(mockWebViewCompatWrapper).addWebMessageListener(
            eq(mockWebView),
            eq("testObject"),
            eq(setOf("*")),
            any(),
        )
    }

    @Test
    fun `when registering and feature disabled then do not register`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = false)
        plugin = testee.createPlugin(mockStrategy)

        plugin.register(mockWebViewCompatMessageCallback, mockWebView)

        verify(mockWebViewCompatWrapper, never()).addWebMessageListener(any(), any(), any(), any())
    }

    @Test
    fun `when unregistering and feature enabled then unregister web message listener`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)

        plugin.unregister(mockWebView)

        verify(mockWebViewCompatWrapper).removeWebMessageListener(mockWebView, "testObject")
    }

    @Test
    fun `when unregistering and feature disabled then do not unregister`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = false)
        plugin = testee.createPlugin(mockStrategy)

        plugin.unregister(mockWebView)

        verify(mockWebViewCompatWrapper, never()).removeWebMessageListener(any(), any())
    }

    @Test
    fun `when posting message and feature enabled but no initialPing then do not post message`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)
        val eventData = com.duckduckgo.js.messaging.api.SubscriptionEventData("feature", "subscription", JSONObject())

        plugin.postMessage(mockWebView, eventData)

        verify(mockReplyProxy, never()).postMessage(anyString())
    }

    @Test
    fun `when posting message and feature disabled then do not post message`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = false)
        plugin = testee.createPlugin(mockStrategy)
        val eventData = com.duckduckgo.js.messaging.api.SubscriptionEventData("feature", "subscription", JSONObject())

        plugin.postMessage(mockWebView, eventData)

        verify(mockReplyProxy, never()).postMessage(anyString())
    }

    @Test
    fun `when processing valid message then execute callback`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)
        val message = """
            {"context":"testContext","featureName":"testFeature","id":"testId","method":"testMethod","params":{}}
        """.trimIndent()
        plugin.register(mockWebViewCompatMessageCallback, mockWebView)

        val listenerCaptor = argumentCaptor<WebMessageListener>()

        verify(mockWebViewCompatWrapper).addWebMessageListener(
            eq(mockWebView),
            eq("testObject"),
            eq(setOf("*")),
            listenerCaptor.capture(),
        )

        listenerCaptor.firstValue.onPostMessage(
            mockWebView,
            WebMessageCompat(message),
            mock(),
            true,
            mockReplyProxy,
        )

        verify(mockWebViewCompatMessageCallback).process(
            eq("testContext"),
            eq("testFeature"),
            eq("testMethod"),
            eq("testId"),
            any(),
            any(),
        )
    }

    @Test
    fun `when processing invalid message then do nothing`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)
        plugin.register(mockWebViewCompatMessageCallback, mockWebView)

        val listenerCaptor = argumentCaptor<WebMessageListener>()

        verify(mockWebViewCompatWrapper).addWebMessageListener(
            eq(mockWebView),
            eq("testObject"),
            eq(setOf("*")),
            listenerCaptor.capture(),
        )

        listenerCaptor.firstValue.onPostMessage(
            mockWebView,
            WebMessageCompat(""),
            mock(),
            true,
            mockReplyProxy,
        )

        verify(mockWebViewCompatMessageCallback, never()).process(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `when processing message with wrong context then do nothing`() = runTest {
        val mockStrategy = createMockStrategy(canHandleMessaging = true)
        plugin = testee.createPlugin(mockStrategy)
        val message = """
            {"context":"wrongContext","featureName":"testFeature","id":"testId","method":"testMethod","params":{}}
        """.trimIndent()

        plugin.register(mockWebViewCompatMessageCallback, mockWebView)

        val listenerCaptor = argumentCaptor<WebMessageListener>()

        verify(mockWebViewCompatWrapper).addWebMessageListener(
            eq(mockWebView),
            eq("testObject"),
            eq(setOf("*")),
            listenerCaptor.capture(),
        )

        listenerCaptor.firstValue.onPostMessage(
            mockWebView,
            WebMessageCompat(message),
            mock(),
            true,
            mockReplyProxy,
        )

        verify(mockWebViewCompatMessageCallback, never()).process(any(), any(), any(), any(), any(), any())
    }

    private fun createMockStrategy(canHandleMessaging: Boolean): WebMessagingStrategy {
        return object : WebMessagingStrategy {
            override val context: String = "testContext"
            override val allowedDomains: Set<String> = setOf("*")
            override val objectName: String = "testObject"

            override suspend fun canHandleMessaging(): Boolean = canHandleMessaging

            override fun getMessageHandlers(): List<WebViewCompatMessageHandler> {
                return listOf(
                    object : WebViewCompatMessageHandler {
                        override fun process(jsMessage: JsMessage): ProcessResult {
                            return SendToConsumer
                        }

                        override val featureName: String = "testFeature"
                        override val methods: List<String> = listOf("testMethod")
                    },
                )
            }

            override fun getGlobalMessageHandler(): List<GlobalJsMessageHandler> {
                return listOf(
                    object : GlobalJsMessageHandler {
                        override fun process(jsMessage: JsMessage): ProcessResult {
                            return SendToConsumer
                        }

                        override val method: String = "globalMethod"
                    },
                )
            }
        }
    }
}
