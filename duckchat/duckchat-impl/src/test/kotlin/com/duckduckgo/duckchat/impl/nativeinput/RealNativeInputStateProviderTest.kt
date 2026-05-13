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

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealNativeInputStateProviderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val duckAiChatStore: DuckAiChatStore = mock()
    private lateinit var testee: RealNativeInputStateProvider

    @Before
    fun setUp() {
        testee = RealNativeInputStateProvider(duckAiChatStore, TestScope(coroutineRule.testDispatcher))
    }

    @Test
    fun whenDisplayedStateInitialThenZero() {
        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenStateForUnknownTabThenZero() {
        assertEquals(NativeInputState.zero(), testee.stateForTab("new-tab").value)
    }

    @Test
    fun whenSetActiveTabThenDisplayedStateUpdated() {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.updateActiveTab("tab-1", structural)

        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.displayedState.value.inputMode)
        assertEquals(NativeInputState.InputContext.DUCK_AI, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenSetActiveTabThenExistingTabFieldsPreserved() {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "claude-3", chatId = "chat-1") }

        testee.updateActiveTab(
            "tab-1",
            structural(context = NativeInputState.InputContext.DUCK_AI),
        )

        val state = testee.displayedState.value
        assertEquals("claude-3", state.selectedModelId)
        assertEquals("chat-1", state.chatId)
        assertEquals(NativeInputState.InputContext.DUCK_AI, state.inputContext)
    }

    @Test
    fun whenSetActiveTabFirstTimeThenSelectedModelIdIsNull() {
        testee.updateActiveTab("tab-1", structural())

        assertNull(testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateOnActiveTabThenDisplayedStateReflectsChange() {
        testee.updateActiveTab("tab-1", structural())

        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }

        assertEquals("gpt-4o", testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateChatIdOnActiveTabThenDisplayedStateReflectsChange() {
        testee.updateActiveTab("tab-1", structural())

        testee.update("tab-1") { copy(chatId = "chat-42") }

        assertEquals("chat-42", testee.displayedState.value.chatId)
    }

    @Test
    fun whenUpdateOnUnknownTabThenEntryCreatedAndDisplayedStateUntouched() {
        testee.update("never-set-tab") { copy(selectedModelId = "model") }

        assertEquals("model", testee.stateForTab("never-set-tab").value.selectedModelId)
        // No active tab yet, so the ambient displayed state is unaffected.
        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenUpdateBeforeSetActiveTabThenSetActiveTabPreservesPatchedFields() {
        testee.update("tab-1") { copy(chatId = "chat-77", selectedModelId = "claude-3") }

        testee.updateActiveTab("tab-1", structural(context = NativeInputState.InputContext.DUCK_AI))

        val state = testee.displayedState.value
        assertEquals("chat-77", state.chatId)
        assertEquals("claude-3", state.selectedModelId)
        assertEquals(NativeInputState.InputContext.DUCK_AI, state.inputContext)
    }

    @Test
    fun whenClearActiveTabThenDisplayedStateResetsToZero() {
        testee.updateActiveTab("tab-1", structural(context = NativeInputState.InputContext.DUCK_AI))
        testee.clearTab("tab-1")

        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearNonActiveTabThenDisplayedStateUnchanged() {
        testee.updateActiveTab("tab-1", structural())
        testee.updateActiveTab("tab-2", structural(context = NativeInputState.InputContext.DUCK_AI))

        testee.clearTab("tab-1")

        assertEquals(NativeInputState.InputContext.DUCK_AI, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenStateForTabAfterUpdateThenReturnsUpdatedState() = runTest {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "llama-3") }

        assertEquals("llama-3", testee.stateForTab("tab-1").first().selectedModelId)
    }

    @Test
    fun whenClearAllThenDisplayedStateResetsToZero() {
        testee.updateActiveTab("tab-1", structural(context = NativeInputState.InputContext.DUCK_AI))

        testee.clearAll()

        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearAllThenAllPerTabEntriesDropped() {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }
        testee.update("tab-2") { copy(selectedModelId = "claude-3") }

        testee.clearAll()

        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-1").value)
        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-2").value)
    }

    @Test
    fun whenClearedTabIsRequestedAgainThenZero() {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }
        testee.clearTab("tab-1")

        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-1").value)
    }

    @Test
    fun whenLoadChatStateWithKnownChatThenChatIdAndModelHydrated() = runTest {
        testee.updateActiveTab("tab-1", structural())
        whenever(duckAiChatStore.getChat("chat-1")).thenReturn(chatWith(id = "chat-1", model = "gpt-4o"))

        testee.updateFromChat("tab-1", "chat-1")

        val state = testee.displayedState.value
        assertEquals("chat-1", state.chatId)
        assertEquals("gpt-4o", state.selectedModelId)
    }

    @Test
    fun whenLoadChatStateWithUnknownChatThenChatIdSetAndExistingModelKept() = runTest {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "user-pick") }
        whenever(duckAiChatStore.getChat("chat-1")).thenReturn(null)

        testee.updateFromChat("tab-1", "chat-1")

        val state = testee.displayedState.value
        assertEquals("chat-1", state.chatId)
        assertEquals("user-pick", state.selectedModelId)
    }

    @Test
    fun whenLoadChatStateWithChatMissingModelThenExistingModelKept() = runTest {
        testee.updateActiveTab("tab-1", structural())
        testee.update("tab-1") { copy(selectedModelId = "user-pick") }
        whenever(duckAiChatStore.getChat("chat-1")).thenReturn(chatWith(id = "chat-1", model = ""))

        testee.updateFromChat("tab-1", "chat-1")

        val state = testee.displayedState.value
        assertEquals("chat-1", state.chatId)
        assertEquals("user-pick", state.selectedModelId)
    }

    @Test
    fun whenLoadChatStateBeforeWidgetAttachesThenStateBufferedForLaterSetActiveTab() = runTest {
        whenever(duckAiChatStore.getChat("chat-1")).thenReturn(chatWith(id = "chat-1", model = "gpt-4o"))

        testee.updateFromChat("tab-1", "chat-1")
        testee.updateActiveTab("tab-1", structural())

        val state = testee.displayedState.value
        assertEquals("chat-1", state.chatId)
        assertEquals("gpt-4o", state.selectedModelId)
    }

    private fun chatWith(id: String, model: String) = DuckAiChat(
        chatId = id,
        title = "title",
        model = model,
        lastEdit = "2026-04-01T00:00:00.000Z",
        pinned = false,
    )

    private fun structural(
        mode: NativeInputState.InputMode = NativeInputState.InputMode.SEARCH_ONLY,
        context: NativeInputState.InputContext = NativeInputState.InputContext.BROWSER,
    ) = NativeInputState(inputMode = mode, inputContext = context)
}
