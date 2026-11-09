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
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommand
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintCommands
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsEligibility
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHintsUsage
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NewChatHintsViewModelTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val eligible = MutableStateFlow(true)
    private val commands = NewChatHintCommands()
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
    private val usage: NewChatHintsUsage = mock()
    private fun viewModel(
        provider: NativeInputStateProvider = stateStore,
        eligibility: NewChatHintsEligibility = object : NewChatHintsEligibility {
            override fun observeEligible(browserMode: BrowserMode) = eligible
        },
        browserMode: BrowserMode = BrowserMode.REGULAR,
    ) = NewChatHintsViewModel(provider, modelManager, duckChat, commands, tabRepository, usage, eligibility, browserMode)

    @Before
    fun setUp() {
        stateStore.publish("tab", NativeInputState.zero().copy(inputContext = InputContext.DUCK_AI))
    }

    @Test
    fun whenTypingThenHintsHideAndReturnInTheSameOrderOnceTheInputIsEmpty() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            assertEquals(setOf(NewChatHint.DOCUMENTS, NewChatHint.PHOTO, NewChatHint.IMAGE_GENERATION), initial.toSet())
            stateStore.update("tab") { it.copy(hasText = true) }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(hasText = false) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAnAttachmentIsAddedThenHintsHideUntilItIsRemoved() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            stateStore.update("tab") { it.copy(hasAttachments = true) }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(hasAttachments = false) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAToolIsSelectedThenHintsHideUntilItIsDeselected() = runTest {
        viewModel().viewState("tab").test {
            val initial = awaitItem()
            stateStore.update("tab") { it.copy(selectedTool = Tool.IMAGE_GENERATION.rawValue) }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(selectedTool = null) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAFilesHintIsTappedWithoutAttachingThenHintsStayVisible() = runTest {
        val testee = viewModel()
        testee.viewState("tab").test {
            awaitItem()
            testee.onHintTapped(NewChatHint.DOCUMENTS, "tab")
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateImageIsTappedThenHintsHideUntilImageGenerationIsRemoved() = runTest {
        val testee = viewModel()
        testee.viewState("tab").test {
            val initial = awaitItem()
            testee.onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            stateStore.update("tab") { it.copy(selectedTool = Tool.IMAGE_GENERATION.rawValue) }
            assertTrue(awaitItem().isEmpty())
            stateStore.update("tab") { it.copy(selectedTool = null) }
            assertEquals(initial, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenATapIsStillBeingHandledThenASecondTapIsIgnored() = runTest {
        val selection = CompletableDeferred<Unit>()
        whenever(modelManager.selectModel(any())).doSuspendableAnswer { selection.await() }
        val testee = viewModel()
        commands.commandsFor { "tab" }.test {
            backgroundScope.launch { testee.onHintTapped(NewChatHint.DOCUMENTS, "tab") }
            runCurrent()
            testee.onHintTapped(NewChatHint.PHOTO, "tab")
            expectNoEvents()
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
    fun whenAModelChangeWindowIsOpenThenHintsAreHiddenAndTapsSendNothing() = runTest {
        stateStore.update("tab") { it.copy(modelChangeMode = true) }
        assertTrue(viewModel().viewState("tab").first().isEmpty())
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
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
    fun whenDocumentsHintIsTappedThenFirstAccessibleFileModelIsSelectedBeforeOpeningPicker() = runTest {
        val unsupported = model().copy(id = "text", supportedFileTypes = emptyList())
        val inaccessible = model().copy(id = "gated", isAccessible = false)
        val capable = model().copy(id = "files")
        models.value = ModelState(models = listOf(unsupported, inaccessible, capable), selectedModelId = "text")
        whenever(modelManager.selectModel(any())).thenAnswer { invocation ->
            models.value = models.value.copy(selectedModelId = invocation.getArgument<AIChatModel>(0).id)
            Unit
        }
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.DOCUMENTS, "tab")
            assertEquals("files", models.value.selectedModelId)
            assertEquals(NewChatHintCommand.OpenDocuments("tab", listOf("application/pdf")), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPhotoHintIsTappedThenGalleryCommandIsSentAndTheDraftIsKept() = runTest {
        models.value = models.value.copy(selectedModelId = "model")
        stateStore.update("tab") { it.copy(hasText = true) }
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.PHOTO, "tab")
            assertEquals(NewChatHintCommand.OpenPhoto("tab"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(input.value.hasText)
    }

    @Test
    fun whenImageHintIsTappedThenOnlyItsTabReceivesTheCommand() = runTest {
        commands.commandsFor { "other" }.test {
            val otherTab = this
            commands.commandsFor { "tab" }.test {
                viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
                assertEquals(NewChatHintCommand.CreateImage("tab"), awaitItem())
                otherTab.expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCapabilityDisappearsBeforeTheTapThenNoCommandIsSent() = runTest {
        models.value = ModelState()
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.DOCUMENTS, "tab")
            viewModel().onHintTapped(NewChatHint.PHOTO, "tab")
            viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTabSwitchesDuringModelSelectionThenNoCommandIsSentToThePreviousTab() = runTest {
        whenever(modelManager.selectModel(any())).thenAnswer {
            selectedTab.value = TabEntity("other")
            Unit
        }
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            expectNoEvents()
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

    @Test
    fun whenNotEligibleThenTapsDoNotSendCommands() = runTest {
        eligible.value = false
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            viewModel().onHintTapped(NewChatHint.PHOTO, "tab")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(viewModel().viewState("tab").first().isEmpty())
    }

    @Test
    fun whenEligibilityResetsDuringModelSelectionThenNoCommandIsSent() = runTest {
        whenever(modelManager.selectModel(any())).thenAnswer {
            eligible.value = false
            Unit
        }
        commands.commandsFor { "tab" }.test {
            viewModel().onHintTapped(NewChatHint.IMAGE_GENERATION, "tab")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectionChangesDuringStateReadThenCommandsNeverTargetANonSelectedTab() = runTest {
        val selectedInput = CompletableDeferred<NativeInputState>()
        val provider = object : NativeInputStateProvider by stateStore {
            override val state = flow { emit(selectedInput.await()) }
        }
        val deliveries = mutableListOf<Pair<String, String?>>()
        commands.commandsFor { "tab" }
            .onEach { deliveries += it.tabId to selectedTab.value?.tabId }
            .launchIn(backgroundScope)
        backgroundScope.launch { viewModel(provider = provider).onHintTapped(NewChatHint.IMAGE_GENERATION, "tab") }
        runCurrent()

        selectedTab.value = TabEntity("other")
        selectedInput.complete(NativeInputState.zero().copy(inputContext = InputContext.DUCK_AI))
        runCurrent()

        assertTrue(deliveries.all { (target, selected) -> target == selected })
    }

    @Test
    fun whenHintsAreShownInRegularModeThenUsageIsTold() = runTest {
        viewModel().viewState("tab").first()

        verify(usage).onHintsDisplayed("tab")
    }

    @Test
    fun whenNoHintsAreShownThenUsageIsNotTold() = runTest {
        eligible.value = false

        viewModel().viewState("tab").first()

        verify(usage, never()).onHintsDisplayed(any())
    }

    @Test
    fun whenHintsAreShownInFireModeThenUsageIsNotTold() = runTest {
        viewModel(browserMode = BrowserMode.FIRE).viewState("tab").first()

        verify(usage, never()).onHintsDisplayed(any())
    }

    @Test
    fun whenAHintIsTappedThenUsageIsToldEvenIfNoCommandIsSent() = runTest {
        eligible.value = false

        viewModel().onHintTapped(NewChatHint.PHOTO, "tab")

        verify(usage).onHintTapped("tab")
    }

    @Test
    fun whenAHintIsTappedInFireModeThenUsageIsNotTold() = runTest {
        viewModel(browserMode = BrowserMode.FIRE).onHintTapped(NewChatHint.PHOTO, "tab")

        verify(usage, never()).onHintTapped(any())
    }

    private fun model() = AIChatModel(
        id = "model", name = "Model", displayName = "Model", shortName = "Model",
        accessTier = emptyList(), isAccessible = true, supportsImageUpload = true,
        supportedFileTypes = listOf("application/pdf"), supportedTools = listOf(Tool.IMAGE_GENERATION),
    )
}
