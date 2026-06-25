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

package com.duckduckgo.duckchat.impl.sync

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RealDuckChatSyncRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val metadataStore = object : DuckChatSyncMetadataStore {
        override var deletionTimestamp: String? = null
        override var pendingChatDeletions: Set<String> = emptySet()
        override var pendingChatUpdates: Set<String> = emptySet()
    }
    private val modeFlow = MutableStateFlow(BrowserMode.REGULAR)
    private val browserModeStateHolder = object : BrowserModeStateHolder {
        override val currentMode: StateFlow<BrowserMode> = modeFlow
        override fun switchTo(mode: BrowserMode) {
            modeFlow.value = mode
        }
    }
    private val repository = RealDuckChatSyncRepository(metadataStore, coroutineTestRule.testDispatcherProvider, browserModeStateHolder)

    // --- REGULAR mode: writes flow through ---

    @Test
    fun `recordSingleChatDeletion enqueues in REGULAR mode`() = runTest {
        modeFlow.value = BrowserMode.REGULAR

        repository.recordSingleChatDeletion("c1")

        assertEquals(setOf("c1"), metadataStore.pendingChatDeletions)
    }

    @Test
    fun `recordSingleChatUpdate enqueues in REGULAR mode`() = runTest {
        modeFlow.value = BrowserMode.REGULAR

        repository.recordSingleChatUpdate("c1")

        assertEquals(setOf("c1"), metadataStore.pendingChatUpdates)
    }

    @Test
    fun `recordDuckAiChatsDeleted records timestamp in REGULAR mode`() = runTest {
        modeFlow.value = BrowserMode.REGULAR

        repository.recordDuckAiChatsDeleted(1_700_000_000_000L)

        assertNotNull(metadataStore.deletionTimestamp)
    }

    // --- FIRE mode: writes are dropped (Fire chats must never enter the sync pipeline) ---

    @Test
    fun `recordSingleChatDeletion is a no-op in FIRE mode`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        repository.recordSingleChatDeletion("fire-chat")

        assertEquals(emptySet<String>(), metadataStore.pendingChatDeletions)
    }

    @Test
    fun `recordSingleChatUpdate is a no-op in FIRE mode`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        repository.recordSingleChatUpdate("fire-chat")

        assertEquals(emptySet<String>(), metadataStore.pendingChatUpdates)
    }

    @Test
    fun `recordDuckAiChatsDeleted is a no-op in FIRE mode`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        repository.recordDuckAiChatsDeleted(1_700_000_000_000L)

        assertNull(metadataStore.deletionTimestamp)
    }
}
