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

package com.duckduckgo.duckchat.bridge.impl

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealDuckAiBridgeManagerTest {

    @Test
    fun `attachToWebView routes incoming message to matching handler`() {
        val received = mutableListOf<String>()
        val handler = object : DuckAiBridgeHandler {
            override val bridgeName = "TestBridge"
            override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {
                received += message
            }
        }
        val webView: WebView = mock()
        val manager = RealDuckAiBridgeManager(setOf(handler))

        manager.attachToWebView(webView)
        manager.simulateMessage("TestBridge", """{"action":"isDone"}""", mock())

        assertEquals(listOf("""{"action":"isDone"}"""), received)
    }

    @Test
    fun `attachToWebView with no handlers makes no WebView calls`() {
        val webView: WebView = mock()
        val manager = RealDuckAiBridgeManager(emptySet())

        manager.attachToWebView(webView)

        verifyNoInteractions(webView)
    }

    @Test
    fun `detachFromWebView is a no-op`() {
        val webView: WebView = mock()
        val manager = RealDuckAiBridgeManager(setOf(fakeHandler("Bridge1")))

        manager.detachFromWebView(webView)

        verifyNoInteractions(webView)
    }

    private fun fakeHandler(name: String) = object : DuckAiBridgeHandler {
        override val bridgeName = name
        override fun onMessage(message: String, replyProxy: JavaScriptReplyProxy) {}
    }
}
