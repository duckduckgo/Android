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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.Toggle
import dagger.Lazy
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

class DelegatingDuckChatDeleterTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val nativeDeleter: NativeDuckChatDeleter = mock()
    private val webViewDeleter: RealDuckChatDeleter = mock()
    private val store: DuckAiChatStore = mock()
    private val feature: DuckChatFeature = mock()
    private val nativeStorageToggle: Toggle = mock()
    private val pixels: DuckChatPixels = mock()
    private lateinit var deleter: DelegatingDuckChatDeleter

    @Before
    fun setup() {
        whenever(feature.useNativeStorageChatData()).thenReturn(nativeStorageToggle)
        deleter = DelegatingDuckChatDeleter(
            nativeDeleter, webViewDeleter, store, feature, Lazy { pixels }, coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `deleteChat uses native deleter when migrated and FF enabled`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(nativeDeleter.deleteChat("chat-1")).thenReturn(true)

        assertTrue(deleter.deleteChat("chat-1"))
        verify(nativeDeleter).deleteChat("chat-1")
        verify(webViewDeleter, never()).deleteChat("chat-1")
    }

    @Test
    fun `deleteChat uses WebView deleter when not migrated`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(true)

        assertTrue(deleter.deleteChat("chat-1"))
        verify(webViewDeleter).deleteChat("chat-1")
        verify(nativeDeleter, never()).deleteChat("chat-1")
    }

    @Test
    fun `deleteChat uses WebView deleter as primary when migrated but FF disabled`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(false)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(true)

        assertTrue(deleter.deleteChat("chat-1"))
        verify(webViewDeleter).deleteChat("chat-1")
    }

    @Test
    fun `deleteChat also deletes from native store when migrated but FF disabled`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(false)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(true)

        deleter.deleteChat("chat-1")

        verify(nativeDeleter).deleteChat("chat-1")
    }

    @Test
    fun `deleteChat does not touch native store when not migrated and FF disabled`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(false)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(true)

        deleter.deleteChat("chat-1")

        verify(nativeDeleter, never()).deleteChat("chat-1")
    }

    @Test
    fun `deleteChat fires native deletion pixel when using native deleter`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(nativeDeleter.deleteChat("chat-1")).thenReturn(true)

        deleter.deleteChat("chat-1")

        verify(pixels).reportNativeStorageDeletionUsed(native = true)
    }

    @Test
    fun `deleteChat fires webview deletion pixel when using webview deleter`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(true)

        deleter.deleteChat("chat-1")

        verify(pixels).reportNativeStorageDeletionUsed(native = false)
    }

    @Test
    fun `deleteChat returns false when native deleter reports chat not found`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(nativeDeleter.deleteChat("missing")).thenReturn(false)

        assertFalse(deleter.deleteChat("missing"))
    }

    @Test
    fun `deleteChat returns false when WebView deleter fails`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(nativeStorageToggle.isEnabled()).thenReturn(true)
        whenever(webViewDeleter.deleteChat("chat-1")).thenReturn(false)

        assertFalse(deleter.deleteChat("chat-1"))
    }
}
