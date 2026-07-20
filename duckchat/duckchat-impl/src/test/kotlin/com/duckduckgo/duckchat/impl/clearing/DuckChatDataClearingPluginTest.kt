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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.sync.DuckChatSyncRepository
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
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
    private val fireChatStore: DuckAiChatStore = mock()
    private val fireModeAvailability: FireModeAvailability = mock()
    private val duckChatSyncRepository: DuckChatSyncRepository = mock()
    private val syncEngine: SyncEngine = mock()
    private val duckChat: DuckChat = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private lateinit var plugin: DuckChatDataClearingPlugin

    @Before
    fun setup() {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1234567890L)
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        plugin = DuckChatDataClearingPlugin(
            duckChatDeleter,
            fireChatStore,
            fireModeAvailability,
            duckChatSyncRepository,
            syncEngine,
            duckChat,
            currentTimeProvider,
            duckChatFeatureRepository,
        )
    }

    // ── Regular mode via AllForMode(REGULAR) ──────────────────────────────────

    @Test
    fun `AllForMode REGULAR deletes all chats and records sync`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(any(), eq(BrowserMode.REGULAR))
        verify(duckChatSyncRepository).clearPendingChatDeletions()
        verify(duckChatSyncRepository).clearPendingChatUpdates()
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `AllForMode REGULAR uses background timestamp when available`() = runTest {
        val backgroundTimestamp = 9999999999L
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)
        whenever(duckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(backgroundTimestamp)

        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))

        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(eq(backgroundTimestamp), eq(BrowserMode.REGULAR))
    }

    @Test
    fun `AllForMode REGULAR falls back to current time when no background timestamp`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)
        whenever(duckChatFeatureRepository.getAppBackgroundTimestamp()).thenReturn(null)

        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))

        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(eq(1234567890L), eq(BrowserMode.REGULAR))
    }

    @Test
    fun `AllForMode REGULAR does not record sync when deletion fails`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.REGULAR)))

        verify(duckChatDeleter).deleteAllChats()
        verify(duckChatSyncRepository, never()).recordDuckAiChatsDeleted(any(), any())
        verify(duckChatSyncRepository, never()).clearPendingChatDeletions()
        verify(duckChatSyncRepository, never()).clearPendingChatUpdates()
        verify(syncEngine, never()).triggerSync(any())
    }

    // ── DuckChats.All routes to both modes ────────────────────────────────────

    @Test
    fun `deleteAllChats All routes to both Regular and Fire when flag on`() = runTest {
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(fireChatStore).deleteAllChats()
        // Sync only for Regular path
        verify(duckChatSyncRepository).recordDuckAiChatsDeleted(any(), eq(BrowserMode.REGULAR))
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `deleteAllChats All routes only Regular when flag off`() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        whenever(duckChatDeleter.deleteAllChats()).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.All))

        verify(duckChatDeleter).deleteAllChats()
        verify(fireChatStore, never()).deleteAllChats()
    }

    // ── Fire mode — AllForMode(FIRE) ──────────────────────────────────────────

    @Test
    fun `AllForMode FIRE calls fireChatStore deleteAllChats and NO sync`() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE)))

        verify(fireChatStore).deleteAllChats()
        verify(duckChatDeleter, never()).deleteAllChats()
        verify(duckChatSyncRepository, never()).recordDuckAiChatsDeleted(any(), any())
        verify(duckChatSyncRepository, never()).clearPendingChatDeletions()
        verify(duckChatSyncRepository, never()).clearPendingChatUpdates()
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `AllForMode FIRE is no-op when flag off`() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.AllForMode(BrowserMode.FIRE)))

        verify(fireChatStore, never()).deleteAllChats()
        verify(duckChatDeleter, never()).deleteAllChats()
        verify(syncEngine, never()).triggerSync(any())
    }

    // ── Fire mode — SelectedForMode(FIRE) ─────────────────────────────────────

    @Test
    fun `SelectedForMode FIRE calls fireChatStore deleteChat and NO sync`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=fire-abc"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.FIRE)))

        verify(fireChatStore).deleteChat("fire-abc")
        verify(duckChatDeleter, never()).deleteChat(any())
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any(), any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `SelectedForMode FIRE deletes each matching chat with no sync`() = runTest {
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://duck.ai?chatID=beta")))).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=alpha", "https://duck.ai?chatID=beta"), BrowserMode.FIRE)),
        )

        verify(fireChatStore).deleteChat("alpha")
        verify(fireChatStore).deleteChat("beta")
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any(), any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `SelectedForMode FIRE is no-op when flag off`() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=fire-abc"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.FIRE)))

        verify(fireChatStore, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    // ── Regular mode via SelectedForMode(REGULAR) — existing tests updated ────

    @Test
    fun `Selected with one chat extracts chatId and deletes`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.REGULAR)))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository).recordSingleChatDeletion(eq("abc-123"), eq(BrowserMode.REGULAR))
        verify(syncEngine).triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
    }

    @Test
    fun `Selected with one chat does nothing when url is not a duck chat url`() = runTest {
        val chatUrl = "https://example.com/?chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.REGULAR)))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected with one chat does nothing when chatId is missing`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.REGULAR)))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected with one chat does not record sync when deletion fails`() = runTest {
        val chatUrl = "https://duckduckgo.com/?ia=chat&chatID=abc-123"
        whenever(duckChat.isDuckChatUrl(Uri.parse(chatUrl))).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("abc-123")).thenReturn(false)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf(chatUrl), BrowserMode.REGULAR)))

        verify(duckChatDeleter).deleteChat("abc-123")
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any(), any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `Selected deletes each chat in the set and records per-chat sync deletions`() = runTest {
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://duck.ai?chatID=beta")))).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("alpha")).thenReturn(true)
        whenever(duckChatDeleter.deleteChat("beta")).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=alpha", "https://duck.ai?chatID=beta"), BrowserMode.REGULAR)),
        )

        verify(duckChatDeleter).deleteChat("alpha")
        verify(duckChatDeleter).deleteChat("beta")
        verify(duckChatSyncRepository).recordSingleChatDeletion(eq("alpha"), eq(BrowserMode.REGULAR))
        verify(duckChatSyncRepository).recordSingleChatDeletion(eq("beta"), eq(BrowserMode.REGULAR))
        // Batched sync trigger — one event for the whole subset, not one per chat.
        verify(syncEngine, times(1)).triggerSync(any())
    }

    @Test
    fun `Selected does not call deleteAllChats`() = runTest {
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(duckChatDeleter.deleteChat(any())).thenReturn(true)

        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=x"), BrowserMode.REGULAR)))

        verify(duckChatDeleter, never()).deleteAllChats()
    }

    @Test
    fun `Selected with empty url set is a no-op`() = runTest {
        plugin.onClearData(setOf(ClearableData.DuckChats.SelectedForMode(emptySet(), BrowserMode.REGULAR)))

        verify(duckChatDeleter, never()).deleteChat(any())
        verify(duckChatSyncRepository, never()).recordSingleChatDeletion(any(), any())
        verify(syncEngine, never()).triggerSync(any())
    }

    @Test
    fun `ignores unrelated clearable data types`() = runTest {
        plugin.onClearData(setOf(ClearableData.Tabs.All, ClearableData.BrowserData.All))

        verify(duckChatDeleter, never()).deleteAllChats()
        verify(duckChatDeleter, never()).deleteChat(any())
    }

    @Test
    fun `DuckChats Selected skips urls that are not duck chat urls`() = runTest {
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://duck.ai?chatID=alpha")))).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(eq(Uri.parse("https://example.com")))).thenReturn(false)
        whenever(duckChatDeleter.deleteChat("alpha")).thenReturn(true)

        plugin.onClearData(
            setOf(ClearableData.DuckChats.SelectedForMode(setOf("https://duck.ai?chatID=alpha", "https://example.com"), BrowserMode.REGULAR)),
        )

        verify(duckChatDeleter).deleteChat("alpha")
        verify(duckChatDeleter, never()).deleteChat(eq(""))
    }
}
