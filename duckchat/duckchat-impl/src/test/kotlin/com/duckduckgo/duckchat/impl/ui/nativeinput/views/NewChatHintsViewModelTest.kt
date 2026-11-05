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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import app.cash.turbine.test
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.ChatStateStore
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHint
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsEligibility
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NewChatHintsViewModelTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val eligible = MutableStateFlow(true)
    private val selectedTab = MutableStateFlow<TabEntity?>(TabEntity("tab"))
    private val tabRepository: TabRepository = mock { on { flowSelectedTab } doReturn selectedTab }
    private val repositories: BrowserModeDataProvider<TabRepository> = mock {
        on { forMode(BrowserMode.REGULAR) } doReturn tabRepository
    }
    private val mode: BrowserModeStateHolder = mock {
        on { currentMode } doReturn MutableStateFlow(BrowserMode.REGULAR)
    }
    private val stateStore = RealNativeInputStateStore(lazy { repositories }, mode, mock(), ChatStateStore())
    private val input = stateStore.stateForTab("tab")
    private val models = MutableStateFlow(ModelState(models = listOf(model())))
    private val modelManager: DuckAiModelManager = mock { on { modelState } doReturn models }
    private val duckChat: DuckChatInternal = mock { on { isImageUploadEnabled() } doReturn true }
    private fun viewModel(
        provider: NativeInputStateProvider = stateStore,
        eligibility: NewChatHintsEligibility = object : NewChatHintsEligibility {
            override fun observeEligible(browserMode: BrowserMode) = eligible
        },
    ) = NewChatHintsViewModel(provider, modelManager, duckChat, tabRepository, eligibility, BrowserMode.REGULAR)

    @Before
    fun setUp() {
        stateStore.publish("tab", NativeInputState.zero().copy(inputContext = InputContext.DUCK_AI))
    }

    @Test
    fun whenTypingOnNewChatThenAllAvailableHintsStayInTheSameOrder() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            assertEquals(setOf(NewChatHint.DOCUMENTS, NewChatHint.PHOTO, NewChatHint.IMAGE_GENERATION), initial.toSet())
            stateStore.update("tab") { it.copy(hasText = true, hasAttachments = true) }
            runCurrent()
            expectNoEvents()
            assertEquals(initial, viewModel().viewState("tab").first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCapabilitiesAreUnavailableThenTheirHintsAreHidden() = runTest {
        models.value = ModelState(
            models = listOf(
                model().copy(isAccessible = false),
                model().copy(supportsImageUpload = false, supportedTools = emptyList()),
            ),
        )
        assertEquals(listOf(NewChatHint.DOCUMENTS), viewModel().viewState("tab").first())
    }

    @Test
    fun whenInputIsNotANewFullScreenChatThenHintsAreHidden() = runTest {
        listOf(InputContext.BROWSER, InputContext.DUCK_AI_CONTEXTUAL).forEach { context ->
            stateStore.update("tab") { it.copy(inputContext = context) }
            assertTrue(viewModel().viewState("tab").first().isEmpty())
        }
        stateStore.update("tab") { it.copy(inputContext = InputContext.DUCK_AI, chatId = "chat") }
        assertTrue(viewModel().viewState("tab").first().isEmpty())
    }

    @Test
    fun whenViewModelIsRecreatedThenTheTabKeepsItsOrder() = runTest {
        val initial = viewModel().viewState("tab").first()
        assertEquals(initial, viewModel().viewState("tab").first())
    }

    @Test
    fun whenDifferentTabsShowHintsThenTheirOrdersCanDiffer() = runTest {
        val orders = (1..20).map { index ->
            val tabId = "tab-$index"
            stateStore.publish(tabId, NativeInputState.zero().copy(inputContext = InputContext.DUCK_AI))
            selectedTab.value = TabEntity(tabId)
            viewModel().viewState(tabId).first()
        }
        assertTrue(orders.toSet().size > 1)
    }

    @Test
    fun whenPromptIsSentThenHintsDisappearAndTheNextNewChatKeepsTheTabsOrder() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            stateStore.update("tab") { it.copy(chatId = "chat") }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(chatId = null) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabChangesThenThePreviousTabsHintsHideAndKeepTheirOrderOnReturn() = runTest {
        stateStore.publish("other", input.value.copy(chatId = "existing"))
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            selectedTab.value = TabEntity("other")
            assertTrue(awaitItem().isEmpty())
            selectedTab.value = TabEntity("tab")
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenReturningToNewChatAfterBrowsingThenTheTabKeepsItsOrder() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            stateStore.update("tab") { it.copy(inputContext = InputContext.BROWSER) }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(inputContext = InputContext.DUCK_AI) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNotEligibleThenHintsStayHiddenUntilItBecomesEligible() = runTest {
        eligible.value = false
        viewModel().viewState("tab").test {
            assertTrue(awaitItem().isEmpty())
            eligible.value = true
            assertEquals(3, awaitItem().size)
            eligible.value = false
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun model() = AIChatModel(
        id = "model", name = "Model", displayName = "Model", shortName = "Model",
        accessTier = emptyList(), isAccessible = true, supportsImageUpload = true,
        supportedFileTypes = listOf("application/pdf"), supportedTools = listOf(Tool.IMAGE_GENERATION),
    )
}
