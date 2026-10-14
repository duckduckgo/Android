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

package com.duckduckgo.duckchat.internal.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.store.impl.DuckAiMigrationPrefs
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiLongChatsSeederCapabilityPluginTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val chatsDao: DuckAiBridgeChatsDao = mock()
    private val tabRepository: TabRepository = mock()
    private val migrationPrefs: DuckAiMigrationPrefs = mock()
    private val duckChat: DuckChatInternal = mock()

    private val testee = DuckAiLongChatsSeederCapabilityPlugin(
        chatsDao = chatsDao,
        tabRepository = tabRepository,
        migrationPrefs = migrationPrefs,
        duckChat = duckChat,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Before
    fun setup() {
        whenever(migrationPrefs.isMigrationDone(DuckAiMigrationPrefs.CHATS_KEY)).thenReturn(true)
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/chat?chatID=${it.getArgument<String>(0)}" }
    }

    @Test
    fun whenClickedThenSeedsLongChatsWithManyTextParts() = runTest {
        testee.onCapabilityClicked(ApplicationProvider.getApplicationContext())

        val captor = argumentCaptor<DuckAiBridgeChatEntity>()
        verify(chatsDao, times(CHAT_COUNT)).upsert(captor.capture())
        val longest = JSONObject(captor.allValues.last().data)
        val messages = longest.getJSONArray("messages")
        assertEquals(800, messages.length())
        val lastReplyParts = messages.getJSONObject(messages.length() - 1).getJSONArray("parts")
        assertEquals("text", lastReplyParts.getJSONObject(lastReplyParts.length() - 1).getString("type"))
        assertTrue(captor.allValues.all { it.data.length < CURSOR_WINDOW_BYTES })
    }

    @Test
    fun whenClickedTwiceThenEachRunAddsNewChatsAndTabs() = runTest {
        testee.onCapabilityClicked(ApplicationProvider.getApplicationContext())
        testee.onCapabilityClicked(ApplicationProvider.getApplicationContext())

        val captor = argumentCaptor<DuckAiBridgeChatEntity>()
        verify(chatsDao, times(CHAT_COUNT * 2)).upsert(captor.capture())
        assertEquals(CHAT_COUNT * 2, captor.allValues.map { it.chatId }.toSet().size)
        verify(tabRepository, times(CHAT_COUNT * 2)).add(anyOrNull(), any())
    }

    @Test
    fun whenNativeStorageNotMigratedThenNothingSeeded() = runTest {
        whenever(migrationPrefs.isMigrationDone(DuckAiMigrationPrefs.CHATS_KEY)).thenReturn(false)

        testee.onCapabilityClicked(ApplicationProvider.getApplicationContext())

        verify(chatsDao, never()).upsert(any())
        verify(tabRepository, never()).add(anyOrNull(), any())
    }

    private companion object {
        const val CHAT_COUNT = 5
        const val CURSOR_WINDOW_BYTES = 2 * 1024 * 1024
    }
}
