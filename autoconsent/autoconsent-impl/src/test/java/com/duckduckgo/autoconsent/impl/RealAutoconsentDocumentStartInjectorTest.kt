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

package com.duckduckgo.autoconsent.impl

import android.net.Uri
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.RealAutoconsentDocumentStartInjector.Companion.JS_OBJECT_NAME
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAutoconsentDocumentStartInjectorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pluginPoint = FakePluginPoint()
    private val capabilityChecker: WebViewCapabilityChecker = mock()
    private val webViewCompatWrapper: WebViewCompatWrapper = mock()
    private val webView: WebView = mock()
    private val callback: AutoconsentCallback = mock()

    private val injector = RealAutoconsentDocumentStartInjector(
        pluginPoint,
        capabilityChecker,
        webViewCompatWrapper,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        whenever(capabilityChecker.isSupported(WebViewCapability.WebMessageListener)).thenReturn(true)
        whenever(capabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(true)
        whenever(webViewCompatWrapper.addDocumentStartJavaScript(any(), any(), any())).thenReturn(mock<ScriptHandler>())
    }

    @Test
    fun whenCapabilitiesSupportedThenRegisterBundleInAllFrames() = runTest {
        assertTrue(injector.register(webView, callback))

        val script = argumentCaptor<String>()
        verify(webViewCompatWrapper).addWebMessageListener(eq(webView), eq(JS_OBJECT_NAME), eq(setOf("*")), any())
        verify(webViewCompatWrapper).addDocumentStartJavaScript(eq(webView), script.capture(), eq(setOf("*")))
        assertTrue(script.firstValue.contains(JS_OBJECT_NAME))
        assertTrue(injector.isRegistered(webView))
    }

    @Test
    fun whenDocumentStartScriptNotSupportedThenDoNotRegister() = runTest {
        whenever(capabilityChecker.isSupported(WebViewCapability.DocumentStartJavaScript)).thenReturn(false)

        assertFalse(injector.register(webView, callback))

        verify(webViewCompatWrapper, never()).addWebMessageListener(any(), any(), any(), any())
        assertFalse(injector.isRegistered(webView))
    }

    @Test
    fun whenAddDocumentStartScriptFailsThenRemoveListener() = runTest {
        whenever(webViewCompatWrapper.addDocumentStartJavaScript(any(), any(), any())).thenReturn(null)

        assertFalse(injector.register(webView, callback))

        verify(webViewCompatWrapper).removeWebMessageListener(webView, JS_OBJECT_NAME)
        assertFalse(injector.isRegistered(webView))
    }

    @Test
    fun whenMessageReceivedThenDispatchWithFrameAndReplyToSameProxy() = runTest {
        injector.register(webView, callback)
        val listener = argumentCaptor<WebViewCompat.WebMessageListener>()
        verify(webViewCompatWrapper).addWebMessageListener(any(), any(), any(), listener.capture())
        whenever(webView.url).thenReturn("https://top.example.com/")
        val replyProxy: JavaScriptReplyProxy = mock()

        listener.firstValue.onPostMessage(webView, WebMessageCompat("""{"type":"fake"}"""), Uri.EMPTY, false, replyProxy)

        assertEquals(1, pluginPoint.plugin.count)
        val frame = pluginPoint.plugin.lastFrame!!
        assertFalse(frame.isMainFrame)
        assertEquals("https://top.example.com/", frame.topUrl)

        frame.reply("""{"type":"initResp"}""")
        verify(webViewCompatWrapper).postMessage(webView, replyProxy, """{"type":"initResp"}""")
    }
}
