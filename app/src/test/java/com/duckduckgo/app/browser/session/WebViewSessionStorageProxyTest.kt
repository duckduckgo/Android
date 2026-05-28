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

package com.duckduckgo.app.browser.session

import android.webkit.WebView
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class WebViewSessionStorageProxyTest {

    private val roomBacked: RoomWebViewSessionStorage = mock()
    private val inMemory: InMemoryWebViewSessionStorage = mock()
    private val toggle: Toggle = mock()
    private val browserConfig: AndroidBrowserConfigFeature = mock {
        on { webViewSessionPersistence() } doReturn toggle
    }

    private val proxy = WebViewSessionStorageProxy(
        roomBacked = roomBacked,
        inMemory = inMemory,
        browserConfig = browserConfig,
    )

    private val webView: WebView = mock()

    @Test
    fun whenFeatureEnabledThenSaveRoutesToRoomBacked() {
        whenever(toggle.isEnabled()).thenReturn(true)

        proxy.saveSession(webView, "tab-1")

        verify(roomBacked).saveSession(webView, "tab-1")
        verify(inMemory, never()).saveSession(any(), any())
    }

    @Test
    fun whenFeatureDisabledThenSaveRoutesToInMemory() {
        whenever(toggle.isEnabled()).thenReturn(false)

        proxy.saveSession(webView, "tab-1")

        verify(inMemory).saveSession(webView, "tab-1")
        verify(roomBacked, never()).saveSession(any(), any())
    }

    @Test
    fun whenFeatureEnabledThenRestoreRoutesToRoomBacked() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(roomBacked.restoreSession(any(), any())).thenReturn(true)

        val result = proxy.restoreSession(webView, "tab-1")

        assertTrue(result)
        verifyBlocking(roomBacked) { restoreSession(webView, "tab-1") }
        verifyBlocking(inMemory, never()) { restoreSession(any(), any()) }
    }

    @Test
    fun whenFeatureDisabledThenRestoreRoutesToInMemory() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)
        whenever(inMemory.restoreSession(any(), any())).thenReturn(false)

        val result = proxy.restoreSession(webView, "tab-1")

        assertFalse(result)
        verifyBlocking(inMemory) { restoreSession(webView, "tab-1") }
        verifyBlocking(roomBacked, never()) { restoreSession(any(), any()) }
    }

    @Test
    fun whenFeatureEnabledThenDeleteSessionRoutesToRoomBacked() {
        whenever(toggle.isEnabled()).thenReturn(true)

        proxy.deleteSession("tab-1")

        verify(roomBacked).deleteSession("tab-1")
        verify(inMemory, never()).deleteSession(any())
    }

    @Test
    fun whenFeatureDisabledThenDeleteSessionRoutesToInMemory() {
        whenever(toggle.isEnabled()).thenReturn(false)

        proxy.deleteSession("tab-1")

        verify(inMemory).deleteSession("tab-1")
        verify(roomBacked, never()).deleteSession(any())
    }

    @Test
    fun whenFeatureEnabledThenDeleteAllRoutesToRoomBacked() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)

        proxy.deleteAllSessions()

        verifyBlocking(roomBacked) { deleteAllSessions() }
        verifyBlocking(inMemory, never()) { deleteAllSessions() }
    }

    @Test
    fun whenFeatureDisabledThenDeleteAllRoutesToInMemory() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        proxy.deleteAllSessions()

        verifyBlocking(inMemory) { deleteAllSessions() }
        verifyBlocking(roomBacked, never()) { deleteAllSessions() }
    }
}
