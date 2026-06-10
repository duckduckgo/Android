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

package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebViewCompatContentScopeScriptsConfiguratorTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts = mock()
    private val documentStartPlugin: AddDocumentStartJavaScriptPlugin = mock()
    private val webMessagingPlugin: WebMessagingPlugin = mock()
    private val documentStartPlugins: PluginPoint<AddDocumentStartJavaScriptPlugin> = mock()
    private val webMessagingPlugins: PluginPoint<WebMessagingPlugin> = mock()
    private val webView: WebView = mock()
    private val callback: WebViewCompatMessageCallback = mock()

    private lateinit var testee: RealWebViewCompatContentScopeScriptsConfigurator

    @Before
    fun setUp() {
        whenever(documentStartPlugins.getPlugins()).thenReturn(listOf(documentStartPlugin))
        whenever(webMessagingPlugins.getPlugins()).thenReturn(listOf(webMessagingPlugin))
        testee =
            RealWebViewCompatContentScopeScriptsConfigurator(
                webViewCompatContentScopeScripts = webViewCompatContentScopeScripts,
                documentStartPlugins = documentStartPlugins,
                webMessagingPlugins = webMessagingPlugins,
            )
    }

    @Test
    fun whenDisabledThenConfigureDoesNothing() =
        runTest {
            whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(false)

            testee.configure(webView, callback)

            verify(documentStartPlugin, never()).addDocumentStartJavaScript(webView)
            verify(webMessagingPlugin, never()).register(callback, webView)
        }

    @Test
    fun whenEnabledAndWebMessagingEnabledThenConfigureRegistersPlugins() =
        runTest {
            whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
            whenever(webViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(true)

            testee.configure(webView, callback)

            verify(documentStartPlugin).addDocumentStartJavaScript(webView)
            verify(webMessagingPlugin).register(callback, webView)
        }

    @Test
    fun whenEnabledButWebMessagingDisabledThenOnlyInjectDocumentStart() =
        runTest {
            whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)
            whenever(webViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(false)

            testee.configure(webView, callback)

            verify(documentStartPlugin).addDocumentStartJavaScript(webView)
            verify(webMessagingPlugin, never()).register(callback, webView)
        }

    @Test
    fun isEnabledDelegatesToScriptsProvider() =
        runTest {
            whenever(webViewCompatContentScopeScripts.isEnabled()).thenReturn(true)

            assertTrue(testee.isEnabled())
        }

    @Test
    fun isWebMessagingEnabledDelegatesToScriptsProvider() =
        runTest {
            whenever(webViewCompatContentScopeScripts.isWebMessagingEnabled()).thenReturn(false)

            assertFalse(testee.isWebMessagingEnabled())
        }
}
