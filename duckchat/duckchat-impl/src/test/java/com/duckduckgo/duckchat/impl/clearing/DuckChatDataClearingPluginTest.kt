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

package com.duckduckgo.duckchat.impl.clearing

import com.duckduckgo.dataclearing.api.plugin.ClearResult
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatDataClearingPluginTest {

    @Mock
    private lateinit var mockDuckChat: DuckChatInternal

    @Mock
    private lateinit var mockContextualDataStore: DuckChatContextualDataStore

    private lateinit var plugin: DuckChatDataClearingPlugin

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        runBlocking { whenever(mockDuckChat.deleteChat(any())).thenReturn(true) }
        plugin = DuckChatDataClearingPlugin(mockDuckChat, mockContextualDataStore)
    }

    @Test
    fun whenTabsSingleThenClearContextualMapping() = runTest {
        plugin.onClearData(setOf(ClearableData.Tabs.Single("tab1")))

        verify(mockContextualDataStore).clearTabChatUrl("tab1")
        verify(mockDuckChat, never()).deleteChat(any())
    }

    @Test
    fun whenTabsAllThenClearAllContextualData() = runTest {
        plugin.onClearData(setOf(ClearableData.Tabs.All))

        verify(mockContextualDataStore).clearAll()
    }

    @Test
    fun whenDuckChatsSingleThenDeleteChat() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.Single("https://duck.ai/chat?chatID=abc")))

        verify(mockDuckChat).deleteChat("https://duck.ai/chat?chatID=abc")
    }

    @Test
    fun whenDuckChatsContextualWithExistingUrlThenDeleteChat() = runTest {
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn("https://duck.ai/chat?chatID=ctx-123")

        plugin.onClearData(setOf(ClearableData.DuckChats.Contextual("tab1")))

        verify(mockDuckChat).deleteChat("https://duck.ai/chat?chatID=ctx-123")
    }

    @Test
    fun whenDuckChatsContextualWithNoUrlThenDoNotDeleteChat() = runTest {
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn(null)

        plugin.onClearData(setOf(ClearableData.DuckChats.Contextual("tab1")))

        verify(mockDuckChat, never()).deleteChat(any())
    }

    @Test
    fun whenDuckChatsAllThenNoDirectAction() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(mockDuckChat, never()).deleteChat(any())
        verify(mockContextualDataStore, never()).clearAll()
        verify(mockContextualDataStore, never()).clearTabChatUrl(any())
    }

    @Test
    fun whenBrowserDataThenNoAction() = runTest {
        plugin.onClearData(setOf(ClearableData.BrowserData.All))

        verify(mockDuckChat, never()).deleteChat(any())
        verify(mockContextualDataStore, never()).clearAll()
        verify(mockContextualDataStore, never()).clearTabChatUrl(any())
    }

    @Test
    fun whenMultipleTypesThenHandleEach() = runTest {
        whenever(mockContextualDataStore.getTabChatUrl("tab1")).thenReturn("https://duck.ai/chat?chatID=ctx-456")

        plugin.onClearData(
            setOf(
                ClearableData.Tabs.Single("tab1"),
                ClearableData.DuckChats.Single("https://duck.ai/chat?chatID=main"),
                ClearableData.DuckChats.Contextual("tab1"),
            ),
        )

        verify(mockContextualDataStore).clearTabChatUrl("tab1")
        verify(mockDuckChat).deleteChat("https://duck.ai/chat?chatID=main")
        verify(mockDuckChat).deleteChat("https://duck.ai/chat?chatID=ctx-456")
    }

    @Test
    fun whenOnClearDataThenReturnsSuccess() = runTest {
        val result = plugin.onClearData(setOf(ClearableData.Tabs.Single("tab1")))

        assertTrue(result is ClearResult.Success)
    }
}
