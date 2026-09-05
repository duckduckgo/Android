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

import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EffectiveModelProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val tabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(selectedTabFlow)
    }
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = tabRepository
    }
    private val browserModeStateHolder: BrowserModeStateHolder = mock<BrowserModeStateHolder>().also {
        whenever(it.currentMode).thenReturn(MutableStateFlow(BrowserMode.REGULAR))
    }
    private val store = RealNativeInputStateStore(
        dagger.Lazy { tabRepositoryProvider },
        browserModeStateHolder,
    )
    private val modelStateFlow = MutableStateFlow(
        ModelState(
            models = listOf(model("global-model"), model("chat-model"), model("recovery-model")),
            selectedModelId = "global-model",
            selectedModelShortName = "Global",
        ),
    )
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(modelStateFlow)
    }
    private val duckAiChatStore: DuckAiChatStore = mock()

    private lateinit var testee: RealEffectiveModelProvider

    @Before
    fun setUp() {
        testee = RealEffectiveModelProvider(modelManager, store, duckAiChatStore)
    }

    @Test
    fun whenTabHasNoChatThenGlobalSelectionIsEffective() = runTest {
        publish(NativeInputState.zero())

        assertEquals("global-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenActiveChatHasModelThenChatModelIsEffective() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1"))

        assertEquals("chat-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenChatModelIsNotInTheModelListThenGlobalSelectionIsEffective() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "model-we-lost-access-to"))
        publish(NativeInputState.zero().copy(chatId = "chat-1"))

        assertEquals("global-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenRecoveryModelPickedDuringModelChangeModeThenRecoveryModelIsEffective() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))

        testee.onRecoveryModelPicked(chatId = "chat-1", modelId = "recovery-model")
        advanceUntilIdle()

        assertEquals("recovery-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenModelChangeModeEndsThenRecoveryModelIsIgnored() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))
        testee.onRecoveryModelPicked(chatId = "chat-1", modelId = "recovery-model")
        advanceUntilIdle()

        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = false))

        assertEquals("chat-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenANewModelChangeWindowOpensThenThePreviousRecoveryPickIsNotReused() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))
        testee.onRecoveryModelPicked(chatId = "chat-1", modelId = "recovery-model")
        advanceUntilIdle()
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = false))
        testee.clearRecoveryModelPick(chatId = "chat-1")
        advanceUntilIdle()

        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))

        assertEquals("chat-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenAnotherTabWithoutAnOpenWindowIsSelectedThenThePickSurvives() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        whenever(duckAiChatStore.getChatById("chat-2")).thenReturn(chat("chat-2", "global-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))
        testee.onRecoveryModelPicked(chatId = "chat-1", modelId = "recovery-model")
        advanceUntilIdle()

        // The other tab's window is closed, so its clear must not touch chat-1's pick.
        publish(NativeInputState.zero().copy(chatId = "chat-2", modelChangeMode = false))
        testee.clearRecoveryModelPick(chatId = "chat-2")
        advanceUntilIdle()
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))

        assertEquals("recovery-model", testee.effectiveModelId.first())
    }

    @Test
    fun whenTheWindowBelongsToAnotherChatThenThePickIsNotApplied() = runTest {
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(chat("chat-1", "chat-model"))
        whenever(duckAiChatStore.getChatById("chat-2")).thenReturn(chat("chat-2", "chat-model"))
        publish(NativeInputState.zero().copy(chatId = "chat-1", modelChangeMode = true))
        testee.onRecoveryModelPicked(chatId = "chat-1", modelId = "recovery-model")
        advanceUntilIdle()

        publish(NativeInputState.zero().copy(chatId = "chat-2", modelChangeMode = true))

        assertEquals("chat-model", testee.effectiveModelId.first())
    }

    private fun TestScope.publish(state: NativeInputState) {
        store.publish(TAB_ID, state)
        selectedTabFlow.value = TabEntity(tabId = TAB_ID, position = 0)
        advanceUntilIdle()
    }

    private fun model(id: String): AIChatModel = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = emptyList(),
        isAccessible = true,
    )

    private fun chat(chatId: String, model: String): DuckAiChat = DuckAiChat(
        chatId = chatId,
        title = "title",
        model = model,
        lastEdit = "2026-08-17T00:00:00.000Z",
        pinned = false,
    )

    private companion object {
        const val TAB_ID = "tab-A"
    }
}
