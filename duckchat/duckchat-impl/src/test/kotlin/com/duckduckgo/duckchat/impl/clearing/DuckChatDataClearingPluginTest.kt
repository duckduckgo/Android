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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.sync.DuckChatSyncRepository
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckChatDataClearingPluginTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val duckChatDeleter: DuckChatDeleter = mock()
    private val duckChatSyncRepository: DuckChatSyncRepository = mock()
    private val syncEngine: SyncEngine = mock()
    private val duckChat: DuckChat = mock()
    private lateinit var plugin: DuckChatDataClearingPlugin

    @Before
    fun setup() {
        plugin = DuckChatDataClearingPlugin(duckChatDeleter, duckChatSyncRepository, syncEngine, duckChat)
    }

    @Test
    fun `deleteAllChats deletes all chats and records sync`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(any())
        verify(duckChatSyncRepository).clearPendingChatDeletions()
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `deleteAllChats does not record sync when deletion fails`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository, never()).recordDuckAiChatsDeleted(any())
        verify(duckChatSyncRepository, never()).clearPendingChatDeletions()
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `deleteSingleChat extracts chatId and deletes`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.Single(chatUrl)))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository).recordSingleChatDeletion("abc-123")
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `deleteSingleChat does nothing when url is not a duck chat url`() = runTest {
        val chatUrl = "https://example.com/?chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.Single(chatUrl)))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `deleteSingleChat does nothing when chatId is missing`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.Single(chatUrl)))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `deleteSingleChat does not record sync when deletion fails`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.Single(chatUrl)))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `ignores unrelated clearable data types`() = runTest {
        plugin.onClearData(setOf(ClearableData.Tabs.All, ClearableData.BrowserData.All))

        verify(duckChatDeleter, never()).deleteAllChats()
        verify(duckChatDeleter, never()).deleteChat(any())
    }
}
