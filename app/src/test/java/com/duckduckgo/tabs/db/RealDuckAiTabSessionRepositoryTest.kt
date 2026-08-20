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

package com.duckduckgo.tabs.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.db.DuckAiTabSessionDao
import com.duckduckgo.app.tabs.db.DuckAiTabSessionEntity
import com.duckduckgo.app.tabs.db.RealDuckAiTabSessionRepository
import com.duckduckgo.duckchat.api.DuckChat
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDuckAiTabSessionRepositoryTest {

    private val mockDao: DuckAiTabSessionDao = mock()
    private val mockDuckChat: DuckChat = mock()
    private lateinit var testee: RealDuckAiTabSessionRepository

    @Before
    fun setup() {
        testee = RealDuckAiTabSessionRepository(dao = mockDao, duckChat = Lazy { mockDuckChat })
    }

    @Test
    fun whenTabCreatedWithDuckAiUrlAndPendingSourceThenAttributed() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        testee.setPendingEntryPointSource("browsing_menu_webpage")

        testee.tryClaimEntryPointSource("tab1", "https://duck.ai/chat")

        verify(mockDao).insertOrReplace(DuckAiTabSessionEntity(tabId = "tab1", entryPointSource = "browsing_menu_webpage"))
    }

    @Test
    fun whenTabCreatedWithNoPendingSourceThenNothingStored() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)

        testee.tryClaimEntryPointSource("tab1", "https://duck.ai/chat")

        verify(mockDao, never()).insertOrReplace(any())
    }

    @Test
    fun whenUnrelatedTabRacesInWithNonDuckAiUrlThenPendingSourceSurvivesForTheRealTarget() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)
        testee.setPendingEntryPointSource("browsing_menu_webpage")

        // An unrelated tab creation/navigation happening to run first must not consume the pending
        // value — otherwise the real Duck.ai tab created moments later would lose its attribution.
        testee.tryClaimEntryPointSource("unrelated-tab", "https://example.com")
        verify(mockDao, never()).insertOrReplace(any())

        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        testee.tryClaimEntryPointSource("tab1", "https://duck.ai/chat")

        verify(mockDao).insertOrReplace(DuckAiTabSessionEntity(tabId = "tab1", entryPointSource = "browsing_menu_webpage"))
    }

    @Test
    fun whenPendingSourceAlreadyConsumedThenSecondTabDoesNotClaimIt() = runTest {
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        testee.setPendingEntryPointSource("voice")

        testee.tryClaimEntryPointSource("tab1", "https://duck.ai/chat")
        testee.tryClaimEntryPointSource("tab2", "https://duck.ai/chat")

        verify(mockDao).insertOrReplace(DuckAiTabSessionEntity(tabId = "tab1", entryPointSource = "voice"))
        verify(mockDao, never()).insertOrReplace(DuckAiTabSessionEntity(tabId = "tab2", entryPointSource = "voice"))
    }

    @Test
    fun whenGetEntryPointSourceThenReadsFromDao() = runTest {
        whenever(mockDao.getEntryPointSource("tab1")).thenReturn("chat_history_new_chat")

        val result = testee.getEntryPointSource("tab1")

        assertEquals("chat_history_new_chat", result)
    }
}
