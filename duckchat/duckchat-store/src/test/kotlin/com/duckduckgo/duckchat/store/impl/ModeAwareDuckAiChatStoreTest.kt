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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ModeAwareDuckAiChatStoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val regular: DuckAiChatStore = mock()
    private val fire: DuckAiChatStore = mock()
    private val modeFlow = MutableStateFlow(BrowserMode.REGULAR)
    private val stateHolder = object : BrowserModeStateHolder {
        override val currentMode: StateFlow<BrowserMode> = modeFlow
        override fun switchTo(mode: BrowserMode) {
            modeFlow.value = mode
        }
    }
    private val store = ModeAwareDuckAiChatStore(regular, fire, stateHolder)

    @Test
    fun `delegates to regular store in REGULAR mode`() = runTest {
        modeFlow.value = BrowserMode.REGULAR

        store.getChats()

        verify(regular).getChats()
        verifyNoInteractions(fire)
    }

    @Test
    fun `delegates to fire store in FIRE mode`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.getChats()

        verify(fire).getChats()
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates deleteChat to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.deleteChat("c1")

        verify(fire).deleteChat("c1")
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates writes to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE
        store.renameChat("c1", "new")
        store.pinChat("c2")

        verify(fire).renameChat("c1", "new")
        verify(fire).pinChat("c2")
        verifyNoInteractions(regular)
    }

    @Test
    fun `getChatsFlow follows the current mode store`() = runTest {
        whenever(regular.getChatsFlow()).thenReturn(flowOf(listOf(chat("r"))))
        whenever(fire.getChatsFlow()).thenReturn(flowOf(listOf(chat("f"))))

        modeFlow.value = BrowserMode.REGULAR
        assertEquals(listOf("r"), store.getChatsFlow().first().map { it.chatId })

        modeFlow.value = BrowserMode.FIRE
        assertEquals(listOf("f"), store.getChatsFlow().first().map { it.chatId })
    }

    @Test
    fun `delegates hasMigrated to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.hasMigrated()

        verify(fire).hasMigrated()
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates getChatById to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.getChatById("c1")

        verify(fire).getChatById("c1")
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates getChatContent to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.getChatContent("c1")

        verify(fire).getChatContent("c1")
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates readFileRef to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.readFileRef("uuid")

        verify(fire).readFileRef("uuid")
        verifyNoInteractions(regular)
    }

    @Test
    fun `delegates unpinChat and deleteAllChats to the current mode store`() = runTest {
        modeFlow.value = BrowserMode.FIRE

        store.unpinChat("c1")
        store.deleteAllChats()

        verify(fire).unpinChat("c1")
        verify(fire).deleteAllChats()
        verifyNoInteractions(regular)
    }

    private fun chat(id: String) = DuckAiChat(chatId = id, title = "", model = "", lastEdit = "", pinned = false)
}
