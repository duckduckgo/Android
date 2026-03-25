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

package com.duckduckgo.duckchat.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.localserver.api.DuckAiLocalServer
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiMigrationJsPluginTest {

    private val localServer: DuckAiLocalServer = mock()
    private val feature: DuckChatFeature = mock()
    private val bridgeMigrationToggle: Toggle = mock()
    private val httpServerMigrationToggle: Toggle = mock()
    private val plugin = DuckAiMigrationJsPlugin(localServer, feature)

    @Before
    fun setUp() {
        whenever(feature.bridgeMigration()).thenReturn(bridgeMigrationToggle)
        whenever(feature.httpServerMigration()).thenReturn(httpServerMigrationToggle)
        // Default: HTTP path active, bridge off — preserves existing test assertions
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(false)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(true)
    }

    @Test
    fun `script is injected for duck ai URL`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("__duckAiMigrate"))
        assertTrue(captor.firstValue.contains("127.0.0.1:8080"))
    }

    @Test
    fun `script is injected for duckduckgo com duckchat URL`() {
        whenever(localServer.port).thenReturn(9090)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duckduckgo.com/duckchat/v1/", null)

        verify(webView).evaluateJavascript(any(), isNull())
    }

    @Test
    fun `script is not injected for non-duck-ai URL`() {
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://example.com/", null)

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `script is not injected for null URL`() {
        val webView: WebView = mock()

        plugin.onPageStarted(webView, null, null)

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `script is not injected for URL that contains duck ai as path component`() {
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://example.com/page?url=https://duck.ai", null)

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `script contains savedAIChatData IndexedDB open call`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("savedAIChatData"))
    }

    @Test
    fun `script contains PUT chats endpoint for per-chat upload`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("/chats/"))
    }

    @Test
    fun `script contains chat-images object store name`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("chat-images"))
    }

    @Test
    fun `script contains PUT images endpoint for per-image upload`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("/images/"))
    }

    @Test
    fun `script uses readAsDataURL to convert image Blob`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("readAsDataURL"))
    }

    @Test
    fun `script sends reader result as image data`() {
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("reader.result"))
    }

    @Test
    fun `bridge flag on, HTTP flag off — bridge script injected`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(true)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(false)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("postMessage"))
        assertTrue(captor.firstValue.contains("isDone"))
        assertTrue(captor.firstValue.contains("markDone"))
    }

    @Test
    fun `HTTP flag on, bridge flag off — HTTP script injected`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(false)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(true)
        whenever(localServer.port).thenReturn(8080)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("/migration"))
    }

    @Test
    fun `both flags off — no script injected`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(false)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(false)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        verify(webView, never()).evaluateJavascript(any(), any())
    }

    @Test
    fun `both flags on — bridge script injected (bridge wins)`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(true)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(true)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("postMessage"))
        assertTrue(captor.firstValue.contains("isDone"))
        assertTrue(captor.firstValue.contains("markDone"))
    }

    @Test
    fun `bridge script uses SettingsBridge postMessage for replaceAllSettings`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(true)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(false)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("SettingsBridge"))
        assertTrue(captor.firstValue.contains("replaceAllSettings"))
    }

    @Test
    fun `bridge script uses ChatsBridge postMessage for putChat`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(true)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(false)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("ChatsBridge"))
        assertTrue(captor.firstValue.contains("putChat"))
    }

    @Test
    fun `bridge script uses ImagesBridge postMessage for putImage`() {
        whenever(bridgeMigrationToggle.isEnabled()).thenReturn(true)
        whenever(httpServerMigrationToggle.isEnabled()).thenReturn(false)
        val webView: WebView = mock()

        plugin.onPageStarted(webView, "https://duck.ai/", null)

        val captor = argumentCaptor<String>()
        verify(webView).evaluateJavascript(captor.capture(), isNull())
        assertTrue(captor.firstValue.contains("ImagesBridge"))
        assertTrue(captor.firstValue.contains("putImage"))
    }
}
