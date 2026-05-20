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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.sync.DuckChatSyncRepository
import com.duckduckgo.sync.api.engine.SyncEngine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private lateinit var plugin: DuckChatDataClearingPlugin

    @Before
    fun setup() {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1234567890L)
        plugin = DuckChatDataClearingPlugin(
            duckChatDeleter, duckChatSyncRepository, syncEngine, duckChat, currentTimeProvider, duckChatFeatureRepository,
        )
    }

    @Test
    fun `deleteAllChats deletes all chats and records sync`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(any())
        verify(duckChatSyncRepository).clearPendingChatDeletions()
        verify(duckChatSyncRepository).clearPendingChatUpdates()
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `deleteAllChats uses background timestamp when available`() = runTest {
        val backgroundTimestamp = 9999999999L
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)
        whenever(duckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(backgroundTimestamp)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(backgroundTimestamp)
    }

    @Test
    fun `deleteAllChats falls back to current time when no background timestamp`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)
        whenever(duckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(null)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(1234567890L)
    }

    @Test
    fun `deleteAllChats does not record sync when deletion fails`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository, never()).recordDuckAiChatsDeleted(any())
        verify(duckChatSyncRepository, never()).clearPendingChatDeletions()
        verify(duckChatSyncRepository, never()).clearPendingChatUpdates()
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected with one chat extracts chatId and deletes`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.chatIdOrNull(Uri.parse(chatUrl))).thenReturn("abc-123")
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf(chatUrl))))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository).recordSingleChatDeletion("abc-123")
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `Selected with one chat does nothing when url is not a duck chat url`() = runTest {
        val chatUrl = "https://example.com/?chatID=abc-123"
        whenever(duckChat.chatIdOrNull(Uri.parse(chatUrl))).thenReturn(null)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf(chatUrl))))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected with one chat does nothing when chatId is missing`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat"
        whenever(duckChat.chatIdOrNull(Uri.parse(chatUrl))).thenReturn(null)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf(chatUrl))))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected with one chat does not record sync when deletion fails`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.chatIdOrNull(Uri.parse(chatUrl))).thenReturn("abc-123")
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf(chatUrl))))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected deletes each chat in the set and records per-chat sync deletions`() = runTest {
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn("alpha")
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://duck.ai?chatID=beta")))).thenReturn("beta")
        whenever(duckChatDeleter.deleteChat("alpha")).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("beta")).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=alpha", "https://duck.ai?chatID=beta"))),
        )

        verify(duckChatDeleter).deleteChat("alpha")
        verify(duckChatDeleter).deleteChat("beta")
        verify(duckChatSyncRepository).recordSingleChatDeletion("alpha")
        verify(duckChatSyncRepository).recordSingleChatDeletion("beta")
        // Batched sync trigger — one event for the whole subset, not one per chat.
        verify(syncEngine, times(1)).triggerSync(any())
    }

    @Test
    fun `Selected does not call deleteAllChats`() = runTest {
        whenever(duckChat.chatIdOrNull(any())).thenReturn("x")
        whenever(duckChatDeleter.deleteChat(any())).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=x"))))

        verify(duckChatDeleter, never()).deleteAllChats()
    }

    @Test
    fun `Selected with empty url set is a no-op`() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(emptySet())))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `ignores unrelated clearable data types`() = runTest {
        plugin.onClearData(setOf(ClearableData.Tabs.All, ClearableData.BrowserData.All))

        verify(duckChatDeleter, never()).deleteAllChats()
        verify(duckChatDeleter, never()).deleteChat(any())
    }

    @Test
    fun `DuckChats Selected deletes each chat in the set and records per-chat sync deletions`() = runTest {
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn("alpha")
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://duck.ai?chatID=beta")))).thenReturn("beta")
        whenever(duckChatDeleter.deleteChat("alpha")).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("beta")).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=alpha", "https://duck.ai?chatID=beta"))),
        )

        verify(duckChatDeleter).deleteChat("alpha")
        verify(duckChatDeleter).deleteChat("beta")
        verify(duckChatSyncRepository).recordSingleChatDeletion("alpha")
        verify(duckChatSyncRepository).recordSingleChatDeletion("beta")
        // Batched sync trigger — one event for the whole subset, not one per chat.
        verify(syncEngine, org.mockito.kotlin.times(1)).triggerSync(any())
    }

    @Test
    fun `DuckChats Selected does not call deleteAllChats`() = runTest {
        whenever(duckChat.chatIdOrNull(any())).thenReturn("x")
        whenever(duckChatDeleter.deleteChat(any())).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=x"))))

        verify(duckChatDeleter, never()).deleteAllChats()
    }

    @Test
    fun `DuckChats Selected with empty url set is a no-op`() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.Selected(emptySet())))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `DuckChats Selected skips urls that are not duck chat urls`() = runTest {
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn("alpha")
        whenever(duckChat.chatIdOrNull(eq(Uri.parse("https://example.com")))).thenReturn(null)
        whenever(duckChatDeleter.deleteChat("alpha")).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.Selected(setOf("https://duck.ai?chatID=alpha", "https://example.com"))),
        )

        verify(duckChatDeleter).deleteChat("alpha")
        verify(duckChatDeleter, never()).deleteChat(eq(""))
    }
}
