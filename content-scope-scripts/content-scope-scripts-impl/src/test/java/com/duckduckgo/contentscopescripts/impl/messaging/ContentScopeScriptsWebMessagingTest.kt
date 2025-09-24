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

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessaging
import com.duckduckgo.js.messaging.api.WebMessagingDelegate
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ContentScopeScriptsWebMessagingTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin> = mock()
    private val globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin> = mock()
    private val mockWebView: WebView = mock()
    private val mockWebMessagingDelegate: WebMessagingDelegate = mock()
    private val mockWebMessaging: WebMessaging = mock()
    private lateinit var testee: ContentScopeScriptsWebMessaging

    @Before
    fun setUp() = runTest {
        whenever(mockWebMessagingDelegate.createPlugin(any())).thenReturn(mockWebMessaging)
        testee = ContentScopeScriptsWebMessaging(
            handlers = handlers,
            globalHandlers = globalHandlers,
            webViewCompatContentScopeScripts = webViewCompatContentScopeScripts,
            webMessagingDelegate = mockWebMessagingDelegate,
        )
    }

    @Test
    fun `when register called then delegate to created plugin`() = runTest {
        testee.register(callback, mockWebView)

        verify(mockWebMessaging).register(callback, mockWebView)
    }

    @Test
    fun `when unregister called then delegate to created plugin`() = runTest {
        testee.unregister(mockWebView)

        verify(mockWebMessaging).unregister(mockWebView)
    }

    @Test
    fun `when postMessage called then delegate to created plugin`() = runTest {
        val eventData = SubscriptionEventData("feature", "subscription", JSONObject())

        testee.postMessage(mockWebView, eventData)

        verify(mockWebMessaging).postMessage(mockWebView, eventData)
    }

    @Test
    fun `when constructed then create plugin with correct strategy`() = runTest {
        // Verify that the delegate's createPlugin method was called with a strategy
        verify(mockWebMessagingDelegate).createPlugin(any())
    }

    private val callback = object : WebViewCompatMessageCallback {
        override fun process(
            context: String,
            featureName: String,
            method: String,
            id: String?,
            data: JSONObject?,
            onResponse: suspend (params: JSONObject) -> Unit,
        ) {
            // NOOP for delegation tests
        }
    }
}
