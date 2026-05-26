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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteFactory
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.ReasoningEffort
import com.duckduckgo.duckchat.impl.models.ReasoningEffortAccess
import com.duckduckgo.duckchat.impl.models.ReasoningMode
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NativeInputModeWidgetViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val duckChatInternal: DuckChatInternal = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val subscriptions: Subscriptions = mock()
    private val pendingNativePromptStore: PendingNativePromptStore = mock()
    private val chatSuggestionsReader: ChatSuggestionsReader = mock()
    private val autoCompleteFactory: AutoCompleteFactory = mock()
    private val autoComplete: AutoComplete = mock()
    private val autoCompleteSettings: AutoCompleteSettings = mock()
    private val duckAiChatHistoryFeature: DuckAiChatHistoryFeature = mock()
    private val inputScreenConfigResolver: InputScreenConfigResolver = mock()
    private val pixel: Pixel = mock()
    private val modelManager: DuckAiModelManager = mock()
    private val duckAiChatStore: DuckAiChatStore = mock()

    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val tabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(selectedTabFlow)
    }
    private val realNativeInputStateStore = RealNativeInputStateStore { tabRepository }
    private val nativeInputStatePublisher: NativeInputStatePublisher = realNativeInputStateStore
    private val nativeInputStateProvider: NativeInputStateProvider = realNativeInputStateStore

    private val showSettingsFlow = MutableStateFlow(false)
    private val duckChatUserEnabledFlow = MutableStateFlow(false)
    private val inputScreenUserSettingFlow = MutableStateFlow(false)
    private val chatStateFlow = MutableStateFlow(ChatState.READY)
    private val chatSuggestionsUserEnabledFlow = MutableStateFlow(true)
    private val entitlementsFlow = MutableStateFlow<List<Product>>(emptyList())

    private var fakePlugins: List<NativeInputPlugin> = emptyList()
    private val fakePluginPoint = object : ActivePluginPoint<NativeInputPlugin> {
        override suspend fun getPlugins(): Collection<NativeInputPlugin> = fakePlugins
    }

    private lateinit var testee: NativeInputModeWidgetViewModel

    @Before
    fun setUp() {
        whenever(duckAiFeatureState.showSettings).thenReturn(showSettingsFlow)
        whenever(duckChatInternal.observeEnableDuckChatUserSetting()).thenReturn(duckChatUserEnabledFlow)
        whenever(duckChatInternal.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenUserSettingFlow)
        whenever(duckChatInternal.observeChatSuggestionsUserSettingEnabled()).thenReturn(chatSuggestionsUserEnabledFlow)
        whenever(duckChatInternal.chatState).thenReturn(chatStateFlow)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(entitlementsFlow)
        whenever(autoCompleteFactory.create(any())).thenReturn(autoComplete)
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        whenever(inputScreenConfigResolver.shouldShowInstalledApps()).thenReturn(false)

        testee = createViewModel()
        testee.configure(tabId = "test-tab", isDuckAiMode = false, isBottom = false)
    }

    private fun createViewModel(plugins: List<NativeInputPlugin> = emptyList()): NativeInputModeWidgetViewModel {
        fakePlugins = plugins
        return NativeInputModeWidgetViewModel(
            duckChatInternal = duckChatInternal,
            duckAiFeatureState = duckAiFeatureState,
            subscriptions = subscriptions,
            pendingNativePromptStore = pendingNativePromptStore,
            chatSuggestionsReader = chatSuggestionsReader,
            nativeInputPlugins = fakePluginPoint,
            autoCompleteFactory = autoCompleteFactory,
            autoCompleteSettings = autoCompleteSettings,
            duckAiChatHistoryFeature = duckAiChatHistoryFeature,
            dispatchers = coroutineRule.testDispatcherProvider,
            inputScreenConfigResolver = inputScreenConfigResolver,
            pixel = pixel,
            nativeInputStatePublisher = nativeInputStatePublisher,
            nativeInputStateProvider = nativeInputStateProvider,
            modelManager = modelManager,
            duckAiChatStore = duckAiChatStore,
            appCoroutineScope = TestScope(coroutineRule.testDispatcher),
        )
    }

    @After
    fun tearDownStore() {
        realNativeInputStateStore.clearAll()
    }

    private fun setIsEnabled(enabled: Boolean) {
        showSettingsFlow.value = enabled
        duckChatUserEnabledFlow.value = enabled
    }

    @Test
    fun whenInitialStateThenInputModeReflectsObservedSources() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true

        val freshTestee = createViewModel()
        freshTestee.configure(tabId = "test-tab", isDuckAiMode = false, isBottom = false)

        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, freshTestee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenInitialContextThenBrowser() = runTest {
        assertEquals(NativeInputState.InputContext.BROWSER, testee.state.firstOrNull()!!.inputContext)
    }

    @Test
    fun whenIsEnabledFlowEmitsTrueAndInputScreenSettingTrueThenSearchAndDuckAi() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true

        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenIsEnabledFlowFlipsToFalseThenSearchOnly() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.state.firstOrNull()!!.inputMode)

        setIsEnabled(false)

        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenInputScreenUserSettingFlipsToFalseThenSearchOnly() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.state.firstOrNull()!!.inputMode)

        inputScreenUserSettingFlow.value = false

        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenIsEnabledTrueButInputScreenSettingFalseThenSearchOnly() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = false

        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenChatHistoryAvailableThenIsHistoryAvailableEmitsTrue() = runTest {
        whenever(duckChatInternal.isChatHistoryAvailable()).thenReturn(true)

        val freshTestee = createViewModel()

        assertTrue(freshTestee.isHistoryAvailable.first { it })
    }

    @Test
    fun whenChatHistoryNotAvailableThenIsHistoryAvailableStaysFalse() = runTest {
        whenever(duckChatInternal.isChatHistoryAvailable()).thenReturn(false)

        val freshTestee = createViewModel()
        advanceUntilIdle()

        assertFalse(freshTestee.isHistoryAvailable.value)
    }

    @Test
    fun whenSearchAndDuckAiThenToggleVisible() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true

        assertTrue(testee.state.firstOrNull()!!.toggleVisible)
    }

    @Test
    fun whenSearchOnlyThenToggleNotVisible() = runTest {
        setIsEnabled(false)
        inputScreenUserSettingFlow.value = false

        assertFalse(testee.state.firstOrNull()!!.toggleVisible)
    }

    @Test
    fun whenSetDuckAiModeTrueThenContextIsDuckAi() = runTest {
        testee.setDuckAiMode(true)

        assertEquals(NativeInputState.InputContext.DUCK_AI, testee.state.firstOrNull()!!.inputContext)
    }

    @Test
    fun whenSetDuckAiModeFalseThenContextIsBrowser() = runTest {
        testee.setDuckAiMode(true)
        testee.setDuckAiMode(false)

        assertEquals(NativeInputState.InputContext.BROWSER, testee.state.firstOrNull()!!.inputContext)
    }

    @Test
    fun whenContextIsDuckAiThenDefaultToggleSelectionIsDuckAi() = runTest {
        testee.setDuckAiMode(true)

        assertEquals(NativeInputState.ToggleSelection.DUCK_AI, testee.state.firstOrNull()!!.toggleSelection)
    }

    @Test
    fun whenContextIsBrowserThenDefaultToggleSelectionIsSearch() = runTest {
        assertEquals(NativeInputState.ToggleSelection.SEARCH, testee.state.firstOrNull()!!.toggleSelection)
    }

    @Test
    fun whenContextIsDuckAiAndModeIsSearchAndDuckAiThenToggleVisible() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        testee.setDuckAiMode(true)

        assertTrue(testee.state.firstOrNull()!!.toggleVisible)
    }

    @Test
    fun whenConfigureContextualThenContextIsDuckAiContextual() = runTest {
        testee.configureContextual(tabId = "test-tab")

        assertEquals(NativeInputState.InputContext.DUCK_AI_CONTEXTUAL, testee.state.firstOrNull()!!.inputContext)
    }

    @Test
    fun whenContextIsDuckAiContextualAndModeIsSearchAndDuckAiThenToggleNotVisible() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        testee.configureContextual(tabId = "test-tab")

        assertFalse(testee.state.firstOrNull()!!.toggleVisible)
    }

    @Test
    fun whenContextIsDuckAiContextualThenDefaultToggleSelectionIsDuckAi() = runTest {
        testee.configureContextual(tabId = "test-tab")

        assertEquals(NativeInputState.ToggleSelection.DUCK_AI, testee.state.firstOrNull()!!.toggleSelection)
    }

    @Test
    fun whenSetDuckAiModeThenInputModeUnchanged() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        val initialMode = testee.state.firstOrNull()!!.inputMode

        testee.setDuckAiMode(true)

        assertEquals(initialMode, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenInitialPositionThenTop() = runTest {
        assertEquals(NativeInputState.InputPosition.TOP, testee.state.firstOrNull()!!.inputPosition)
    }

    @Test
    fun whenSetInputPositionBottomThenPositionIsBottom() = runTest {
        testee.setWidgetPosition(isBottom = true)

        assertEquals(NativeInputState.InputPosition.BOTTOM, testee.state.firstOrNull()!!.inputPosition)
    }

    @Test
    fun whenSetInputPositionTopThenPositionIsTop() = runTest {
        testee.setWidgetPosition(isBottom = true)
        testee.setWidgetPosition(isBottom = false)

        assertEquals(NativeInputState.InputPosition.TOP, testee.state.firstOrNull()!!.inputPosition)
    }

    @Test
    fun whenPositionIsBottomThenIsBottomTrue() = runTest {
        testee.setWidgetPosition(isBottom = true)

        assertTrue(testee.state.firstOrNull()!!.isBottom)
    }

    @Test
    fun whenPositionIsTopThenIsBottomFalse() = runTest {
        assertFalse(testee.state.firstOrNull()!!.isBottom)
    }

    @Test
    fun whenSetInputPositionThenInputModeUnchanged() = runTest {
        setIsEnabled(true)
        inputScreenUserSettingFlow.value = true
        val initialMode = testee.state.firstOrNull()!!.inputMode

        testee.setWidgetPosition(isBottom = true)

        assertEquals(initialMode, testee.state.firstOrNull()!!.inputMode)
    }

    @Test
    fun whenConfigureThenBothContextAndPositionSetAtomically() = runTest {
        testee.configure(tabId = "test-tab", isDuckAiMode = true, isBottom = true)

        val state = testee.state.firstOrNull()!!
        assertEquals(NativeInputState.InputContext.DUCK_AI, state.inputContext)
        assertEquals(NativeInputState.InputPosition.BOTTOM, state.inputPosition)
    }

    @Test
    fun whenStorePendingPromptThenDelegatesToStoreWithModelId() = runTest {
        val plugin = fakePlugin(containerId = 1)
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.storePendingPrompt("hello", "model-1", null)

        verify(pendingNativePromptStore).store("hello", "model-1", null, null, emptyList(), emptyList())
    }

    @Test
    fun whenStorePendingPromptWithNoPluginsThenModelIdIsNull() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        viewModel.storePendingPrompt("hello", null, null)

        verify(pendingNativePromptStore).store("hello", null, null, null, emptyList(), emptyList())
    }

    @Test
    fun whenStorePendingPromptWithReasoningEffortThenForwardsEffort() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        viewModel.storePendingPrompt("hello", "model-1", "low")

        verify(pendingNativePromptStore).store("hello", "model-1", "low", null, emptyList(), emptyList())
    }

    @Test
    fun whenCancelChatSuggestionsThenDelegatesToReader() {
        testee.cancelChatSuggestions()

        verify(chatSuggestionsReader).tearDown()
    }

    @Test
    fun whenBuildChatSuggestionUrlThenDelegatesToDuckChat() {
        whenever(duckChatInternal.buildChatUrl("abc-123"))
            .thenReturn("https://duckduckgo.com/?ia=chat&duckai=5&chatID=abc-123")
        val suggestion = ChatSuggestion(
            chatId = "abc-123",
            title = "Title",
            lastEdit = LocalDateTime.now(),
            pinned = false,
        )

        val url = testee.buildChatSuggestionUrl(suggestion)

        assertTrue(url.contains("chatID=abc-123"))
    }

    @Test
    fun whenChatStateFlowEmitsThenViewModelChatStateMirrorsIt() = runTest {
        chatStateFlow.value = ChatState.STREAMING

        assertEquals(ChatState.STREAMING, testee.chatState.firstOrNull()!!)
    }

    @Test
    fun whenChatStateFlowFlipsThenSubsequentValueObserved() = runTest {
        chatStateFlow.value = ChatState.LOADING
        assertEquals(ChatState.LOADING, testee.chatState.firstOrNull()!!)

        chatStateFlow.value = ChatState.HIDE

        assertEquals(ChatState.HIDE, testee.chatState.firstOrNull()!!)
    }

    @Test
    fun whenEntitlementsContainDuckAiPlusThenIsPaidTierTrue() = runTest {
        entitlementsFlow.value = listOf(Product.DuckAiPlus)

        assertTrue(testee.isPaidTier.firstOrNull()!!)
    }

    @Test
    fun whenEntitlementsAreEmptyThenIsPaidTierFalse() = runTest {
        entitlementsFlow.value = emptyList()

        assertFalse(testee.isPaidTier.firstOrNull()!!)
    }

    @Test
    fun whenEntitlementsContainOnlyOtherProductsThenIsPaidTierFalse() = runTest {
        entitlementsFlow.value = listOf(Product.NetP, Product.ITR, Product.PIR)

        assertFalse(testee.isPaidTier.firstOrNull()!!)
    }

    @Test
    fun whenEntitlementsContainDuckAiPlusAlongsideOthersThenIsPaidTierTrue() = runTest {
        entitlementsFlow.value = listOf(Product.NetP, Product.DuckAiPlus, Product.PIR)

        assertTrue(testee.isPaidTier.firstOrNull()!!)
    }

    @Test
    fun whenChatSuggestionsUserEnabledFlowEmitsTrueThenObservedTrue() = runTest {
        chatSuggestionsUserEnabledFlow.value = true

        assertTrue(testee.chatSuggestionsUserEnabled.firstOrNull()!!)
    }

    @Test
    fun whenChatSuggestionsUserEnabledFlowEmitsFalseThenObservedFalse() = runTest {
        chatSuggestionsUserEnabledFlow.value = false

        assertFalse(testee.chatSuggestionsUserEnabled.firstOrNull()!!)
    }

    @Test
    fun whenNoPluginsThenPluginsStateIsEmpty() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        assertTrue(viewModel.plugins.value.isEmpty())
    }

    @Test
    fun whenPluginsExistThenPluginsStateContainsThem() = runTest {
        val plugin = fakePlugin(containerId = 42)
        val viewModel = createViewModel(plugins = listOf(plugin))

        val plugins = viewModel.plugins.value
        assertEquals(1, plugins.size)
        assertEquals(42, plugins[0].containerId)
    }

    @Test
    fun whenModelManagerHasNoSelectedModelThenGetSelectedModelIdReturnsNull() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn(null)
        val viewModel = createViewModel()

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelManagerReportsSelectedModelThenGetSelectedModelIdReturnsIt() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("claude-3")
        val viewModel = createViewModel()

        assertEquals("claude-3", viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelPickerDisabledThenGetSelectedModelIdReturnsNull() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("claude-3")
        val viewModel = createViewModel()

        viewModel.setModelPickerEnabled(false)

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelPickerReEnabledThenGetSelectedModelIdReturnsSelection() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("claude-3")
        val viewModel = createViewModel()

        viewModel.setModelPickerEnabled(false)
        assertNull(viewModel.getSelectedModelId())

        viewModel.setModelPickerEnabled(true)
        assertEquals("claude-3", viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelManagerResolvesReasoningEffortThenGetResolvedReasoningEffortReturnsIt() = runTest {
        whenever(modelManager.getResolvedReasoningEffort()).thenReturn("low")
        val viewModel = createViewModel()

        assertEquals("low", viewModel.getResolvedReasoningEffort())
    }

    @Test
    fun whenModelManagerReturnsNoReasoningEffortThenGetResolvedReasoningEffortReturnsNull() = runTest {
        whenever(modelManager.getResolvedReasoningEffort()).thenReturn(null)
        val viewModel = createViewModel()

        assertNull(viewModel.getResolvedReasoningEffort())
    }

    // region chat-aware submission getters

    @Test
    fun whenChatIdSetThenGetSelectedModelIdReturnsChatsModel() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val modelStateFlow = MutableStateFlow(
            ModelState(models = listOf(aiModel(id = "chat-model", supported = listOf(ReasoningEffort.NONE)))),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "chat-model", lastEdit = "now", pinned = false),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        advanceUntilIdle()

        assertEquals("chat-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatModelNotInModelsListThenGetSelectedModelIdFallsBackToGlobal() = runTest {
        // Chat references "missing-model" no longer offered server-side. Submission must fall back to
        // global on both halves — chat-model + global-effort would be a mismatched pair.
        // Picker is disabled here to mirror production (host binds modelPickerEnabled to chatId == null).
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val modelStateFlow = MutableStateFlow(
            ModelState(
                models = listOf(aiModel(id = "other-model", supported = listOf(ReasoningEffort.NONE))),
            ),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "missing-model", lastEdit = "now", pinned = false),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        viewModel.setModelPickerEnabled(false)
        advanceUntilIdle()

        assertEquals("global-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatIdSetAndPickerDisabledThenGetSelectedModelIdStillReturnsChatsModel() = runTest {
        // Production binds modelPickerEnabled to `chatId == null`, so existing chats always have the
        // picker disabled. Submission must still carry the chat's stored model.
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val modelStateFlow = MutableStateFlow(
            ModelState(models = listOf(aiModel(id = "chat-model", supported = listOf(ReasoningEffort.NONE)))),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "chat-model", lastEdit = "now", pinned = false),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        viewModel.setModelPickerEnabled(false)
        advanceUntilIdle()

        assertEquals("chat-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatIdNullAndPickerDisabledThenGetSelectedModelIdReturnsNull() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = false, isBottom = false)
        viewModel.setModelPickerEnabled(false)
        advanceUntilIdle()

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatIdFlipsAndNewLookupInFlightThenGetSelectedModelIdFallsBackToGlobalNotPreviousChat() = runTest {
        // Warm transition: currentChat is still chat-A while _chatId is "chat-B" and the lookup
        // for chat-B is suspended. Submission must fall back to global rather than return chat-A's
        // model tagged to chat-B.
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val modelStateFlow = MutableStateFlow(
            ModelState(
                models = listOf(
                    aiModel(id = "chat-A-model", supported = listOf(ReasoningEffort.NONE)),
                    aiModel(id = "chat-B-model", supported = listOf(ReasoningEffort.NONE)),
                ),
            ),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-A")).thenReturn(
            DuckAiChat(chatId = "chat-A", title = "t", model = "chat-A-model", lastEdit = "now", pinned = false),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-A")
        advanceUntilIdle()
        assertEquals("chat-A-model", viewModel.getSelectedModelId())

        val pending = CompletableDeferred<DuckAiChat?>()
        duckAiChatStore.stub {
            onBlocking { getChatById("chat-B") } doSuspendableAnswer { pending.await() }
        }
        viewModel.setActiveChatId("chat-B")
        advanceUntilIdle()

        // During the transition: currentChat is null, submission falls back to global.
        assertEquals("global-model", viewModel.getSelectedModelId())

        pending.complete(
            DuckAiChat(chatId = "chat-B", title = "t", model = "chat-B-model", lastEdit = "now", pinned = false),
        )
        advanceUntilIdle()
        assertEquals("chat-B-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatIdJustSetAndLookupInFlightThenSubmissionFallsBackToGlobal() = runTest {
        // setActiveChatId nulls currentChat synchronously and launches the lookup. During the in-
        // flight window, submission must fall back to global rather than return chat-A's data.
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val modelStateFlow = MutableStateFlow(
            ModelState(models = listOf(aiModel(id = "chat-A-model", supported = listOf(ReasoningEffort.NONE)))),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-A")).thenReturn(
            DuckAiChat(chatId = "chat-A", title = "t", model = "chat-A-model", lastEdit = "now", pinned = false),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-A")
        advanceUntilIdle()
        assertEquals("chat-A-model", viewModel.getSelectedModelId())

        // Flip to chat-B without yielding — currentChat is now null, lookup is launched but unfinished.
        viewModel.setActiveChatId("chat-B")
        // Intentionally no advanceUntilIdle() here.

        assertEquals("global-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatIdNullThenGetSelectedModelIdReturnsGlobal() = runTest {
        whenever(modelManager.getSelectedModelId()).thenReturn("global-model")
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = false, isBottom = false)
        viewModel.setActiveChatId(null)
        advanceUntilIdle()

        assertEquals("global-model", viewModel.getSelectedModelId())
    }

    @Test
    fun whenChatHasReasoningModeThenGetResolvedReasoningEffortMatchesChatsMode() = runTest {
        val modelStateFlow = MutableStateFlow(
            ModelState(
                models = listOf(
                    aiModel(id = "chat-model", supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
                ),
            ),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.REASONING.rawValue,
            ),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        advanceUntilIdle()

        assertEquals(ReasoningEffort.LOW.rawValue, viewModel.getResolvedReasoningEffort())
    }

    @Test
    fun whenChatStoredModeIsGatedForCurrentTierThenGetResolvedReasoningEffortFallsBackToAccessible() = runTest {
        // Chat was saved with extended_reasoning (PRO-only on this model). Current user is FREE.
        // Submission must NOT send the gated effort; it must fall back to the first accessible mode's
        // effort — mirroring what the picker UI shows via resolveMode's accessibility filter.
        val gatedModel = AIChatModel(
            id = "chat-model",
            name = "chat-model",
            displayName = "chat-model",
            shortName = "chat-model",
            accessTier = listOf("free", "plus", "pro"),
            isAccessible = true,
            supportedReasoningEfforts = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.MEDIUM),
            reasoningEffortAccess = listOf(
                ReasoningEffortAccess(effort = ReasoningEffort.NONE, accessTier = listOf("free", "plus", "pro"), isAccessible = true),
                ReasoningEffortAccess(effort = ReasoningEffort.LOW, accessTier = listOf("free", "plus", "pro"), isAccessible = true),
                ReasoningEffortAccess(effort = ReasoningEffort.MEDIUM, accessTier = listOf("pro"), isAccessible = false),
            ),
        )
        val modelStateFlow = MutableStateFlow(ModelState(models = listOf(gatedModel)))
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.EXTENDED_REASONING.rawValue,
            ),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        advanceUntilIdle()

        // FAST is the first accessible mode (effort NONE). MEDIUM (extended_reasoning) is gated.
        assertEquals(ReasoningEffort.NONE.rawValue, viewModel.getResolvedReasoningEffort())
    }

    @Test
    fun whenChatScopedReasoningModeSetThenGetResolvedReasoningEffortOverridesChatStoredMode() = runTest {
        val modelStateFlow = MutableStateFlow(
            ModelState(
                models = listOf(
                    aiModel(id = "chat-model", supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
                ),
            ),
        )
        whenever(modelManager.modelState).thenReturn(modelStateFlow)
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.FAST.rawValue,
            ),
        )
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-1")
        advanceUntilIdle()
        modelStateFlow.value = modelStateFlow.value.copy(chatScopedReasoningMode = ReasoningMode.REASONING)

        assertEquals(ReasoningEffort.LOW.rawValue, viewModel.getResolvedReasoningEffort())
    }

    // endregion

    // region tab-switch chatId clear

    @Test
    fun whenChatIdChangesThenChatScopedReasoningModeIsClearedOnManager() = runTest {
        val viewModel = createViewModel()
        viewModel.configure(tabId = "tab-A", isDuckAiMode = true, isBottom = false)
        viewModel.setActiveChatId("chat-X")
        advanceUntilIdle()
        // Manager's setter is called every time _chatId emits (including the initial null on configure
        // and the chat-X value). Reset verification to focus on the chatId-Y transition.
        clearInvocations(modelManager)

        viewModel.setActiveChatId("chat-Y")
        advanceUntilIdle()

        verify(modelManager).setChatScopedReasoningMode(null)
    }

    private fun aiModel(id: String, supported: List<ReasoningEffort>): AIChatModel = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = listOf("free", "plus", "pro"),
        isAccessible = true,
        supportedReasoningEfforts = supported,
    )

    // endregion

    @Test
    fun whenNoTabIsActiveThenGetSelectedToolReturnsNull() = runTest {
        val freshViewModel = createViewModel()

        assertNull(freshViewModel.getSelectedTool())
    }

    @Test
    fun whenSelectedToolWasPublishedThenGetSelectedToolReturnsIt() = runTest {
        val tabId = "tab-A"
        val viewModel = createViewModel()
        viewModel.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        advanceUntilIdle()
        viewModel.setSelectedTool("WEB_SEARCH")

        assertEquals("WEB_SEARCH", viewModel.getSelectedTool())
    }

    @Test
    fun whenSelectedToolClearedThenGetSelectedToolReturnsNull() = runTest {
        val tabId = "tab-A"
        val viewModel = createViewModel()
        viewModel.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        advanceUntilIdle()
        viewModel.setSelectedTool("WEB_SEARCH")
        viewModel.setSelectedTool(null)

        assertNull(viewModel.getSelectedTool())
    }

    @Test
    fun whenStorePendingPromptWithSelectedToolThenForwardsTool() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        viewModel.storePendingPrompt("hello", "model-1", null, selectedTool = "GenerateImage")

        verify(pendingNativePromptStore).store("hello", "model-1", null, "GenerateImage", emptyList(), emptyList())
    }

    @Test
    fun whenUpdatePluginContainerVisibilityThenSendsCommand() = runTest {
        val plugin = fakePlugin(containerId = 99)
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.updatePluginContainerVisibility(isChatTab = true)

        val command = viewModel.commands.firstOrNull()
        assertTrue(command is NativeInputModeWidgetViewModel.Command.UpdatePluginVisibility)
        val update = command as NativeInputModeWidgetViewModel.Command.UpdatePluginVisibility
        assertEquals(listOf(99), update.containerIds)
        assertTrue(update.visible)
    }

    private fun fakePlugin(containerId: Int): NativeInputPlugin {
        return object : NativeInputPlugin {
            override val containerId: Int = containerId
            override fun createView(context: Context, host: NativeInputHost): View = View(context)
        }
    }

    // region fetchChatTabSuggestions

    @Test
    fun whenFetchChatTabSuggestionsThenReturnsBothFetchResults() = runTest {
        val chat = ChatSuggestion(chatId = "id", title = "t", lastEdit = LocalDateTime.now(), pinned = false)
        val url = AutoCompleteBookmarkSuggestion(phrase = "ddg", title = "DDG", url = "https://duckduckgo.com")
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions("query")).thenReturn(listOf(chat))
        whenever(autoComplete.autoComplete("query")).thenReturn(flowOf(AutoCompleteResult("query", listOf(url))))

        val result = testee.fetchChatTabSuggestions(query = "query", chatSuggestionsEnabled = true)

        assertEquals(listOf(chat), result.chatHistory)
        assertEquals(listOf(url), result.urlSuggestions.suggestions)
    }

    @Test
    fun whenChatSuggestionsDisabledThenChatHistoryEmpty() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(autoComplete.autoComplete(any())).thenReturn(flowOf(AutoCompleteResult("q", emptyList())))

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = false)

        assertTrue(result.chatHistory.isEmpty())
    }

    @Test
    fun whenAutoCompleteSettingDisabledThenUrlSuggestionsEmpty() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        assertTrue(result.urlSuggestions.suggestions.isEmpty())
    }

    @Test
    fun whenQueryEmptyThenUrlSuggestionsEmpty() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())

        val result = testee.fetchChatTabSuggestions(query = "", chatSuggestionsEnabled = true)

        assertTrue(result.urlSuggestions.suggestions.isEmpty())
    }

    @Test
    fun whenAutoCompleteFetchFailsThenUrlSuggestionsEmpty() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenThrow(RuntimeException("boom"))

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        assertTrue(result.urlSuggestions.suggestions.isEmpty())
    }

    @Test
    fun whenChatHistoryFetchFailsThenChatHistoryEmpty() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenAnswer { throw RuntimeException("boom") }

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        assertTrue(result.chatHistory.isEmpty())
    }

    @Test
    fun whenFetchChatTabSuggestionsThenFiltersOutDisallowedTypes() = runTest {
        // Three allowed types kept under the default maxUrlSuggestions cap (3) so this test
        // exercises the filter without conflating with the cap (covered separately below).
        val bookmark = AutoCompleteBookmarkSuggestion(phrase = "b", title = "B", url = "https://b")
        val switchToTab = AutoCompleteSwitchToTabSuggestion(phrase = "s", title = "S", url = "https://s", tabId = "1")
        val historyUrl = AutoCompleteHistorySuggestion(phrase = "h", title = "H", url = "https://h", isAllowedInTopHits = true)
        val phraseSearchSuggestion = AutoCompleteSearchSuggestion(phrase = "phrase", isUrl = false, isAllowedInTopHits = true)
        val historySearchSuggestion = AutoCompleteHistorySearchSuggestion(phrase = "hs", isAllowedInTopHits = true)
        val rawSuggestions = listOf(bookmark, switchToTab, historyUrl, phraseSearchSuggestion, historySearchSuggestion)

        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenReturn(flowOf(AutoCompleteResult("q", rawSuggestions)))

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        // search-phrase and history-search are filtered out; bookmark, switch-to-tab, and history-url remain.
        assertEquals(listOf(bookmark, switchToTab, historyUrl), result.urlSuggestions.suggestions)
    }

    @Test
    fun whenFetchChatTabSuggestionsThenSearchSuggestionMarkedAsUrlIsAllowed() = runTest {
        val searchAsUrl = AutoCompleteSearchSuggestion(phrase = "https://x", isUrl = true, isAllowedInTopHits = true)
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenReturn(flowOf(AutoCompleteResult("q", listOf(searchAsUrl))))

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        assertEquals(listOf(searchAsUrl), result.urlSuggestions.suggestions)
    }

    @Test
    fun whenFetchChatTabSuggestionsThenAppliesMaxUrlSuggestionsCap() = runTest {
        // Default cap from DuckAiChatHistoryFeature is 3.
        val urls = (1..10).map {
            AutoCompleteBookmarkSuggestion(phrase = "p$it", title = "t$it", url = "https://x$it")
        }
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenReturn(flowOf(AutoCompleteResult("q", urls)))

        val result = testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        assertEquals(3, result.urlSuggestions.suggestions.size)
        assertEquals(urls.take(3), result.urlSuggestions.suggestions)
    }

    // endregion

    // region fireChatUrlSuggestionPixel

    @Test
    fun whenFireChatUrlSuggestionPixelThenFiresWithLastChatUrlListAndExperimentalFlag() = runTest {
        val url = AutoCompleteBookmarkSuggestion(phrase = "u", title = "U", url = "https://u")
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenReturn(flowOf(AutoCompleteResult("q", listOf(url))))
        // Drive a fetch first so lastChatUrlSuggestions is populated.
        testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        testee.fireChatUrlSuggestionPixel(url)

        verify(autoComplete).fireAutocompletePixel(eq(listOf(url)), eq(url), eq(true))
    }

    @Test
    fun whenFireChatUrlSuggestionPixelWithoutPriorFetchThenFiresWithEmptyList() = runTest {
        val url = AutoCompleteBookmarkSuggestion(phrase = "u", title = "U", url = "https://u")

        testee.fireChatUrlSuggestionPixel(url)

        verify(autoComplete).fireAutocompletePixel(eq(emptyList()), eq(url), eq(true))
    }

    @Test
    fun whenCancelChatSuggestionsThenLastChatUrlListIsReset() = runTest {
        val url = AutoCompleteBookmarkSuggestion(phrase = "u", title = "U", url = "https://u")
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(emptyList())
        whenever(autoComplete.autoComplete("q")).thenReturn(flowOf(AutoCompleteResult("q", listOf(url))))
        testee.fetchChatTabSuggestions(query = "q", chatSuggestionsEnabled = true)

        testee.cancelChatSuggestions()
        testee.fireChatUrlSuggestionPixel(url)

        verify(autoComplete).fireAutocompletePixel(eq(emptyList()), eq(url), eq(true))
    }

    // endregion

    // region publisher.update preserves plugin-owned fields

    @Test
    fun whenWidgetVmPublishesThenPluginOwnedSelectedToolIsPreserved() = runTest {
        val tabId = "tab-A"
        // Plugin writes selectedTool first (via the publisher interface of the same store)
        nativeInputStatePublisher.update(tabId) { it.copy(selectedTool = "WEB_SEARCH") }

        // Widget VM configures and triggers its publish loop
        val viewModel = createViewModel()
        viewModel.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)

        // Allow the combine to emit and the publish loop to run
        advanceUntilIdle()

        // selectedTool must survive the widget VM's update
        assertEquals("WEB_SEARCH", nativeInputStateProvider.stateForTab(tabId).value.selectedTool)
    }

    // endregion

    // region setSelectedTool

    @Test
    fun whenSetSelectedToolThenPublisherUpdatesSelectedToolForActiveTab() = runTest {
        val tabId = "tab-A"
        testee.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        advanceUntilIdle()

        testee.setSelectedTool("WEB_SEARCH")

        assertEquals("WEB_SEARCH", nativeInputStateProvider.stateForTab(tabId).value.selectedTool)
    }

    @Test
    fun whenSetSelectedToolWithNullThenPublisherClearsSelectedTool() = runTest {
        val tabId = "tab-A"
        testee.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        advanceUntilIdle()
        testee.setSelectedTool("WEB_SEARCH")

        testee.setSelectedTool(null)

        assertNull(nativeInputStateProvider.stateForTab(tabId).value.selectedTool)
    }

    @Test
    fun whenSetSelectedToolAndNoActiveTabThenNothingHappens() = runTest {
        val freshViewModel = createViewModel()

        freshViewModel.setSelectedTool("WEB_SEARCH")

        // No active tab → no publisher write. Sample an arbitrary tab to confirm.
        assertNull(nativeInputStateProvider.stateForTab("any-tab").value.selectedTool)
    }

    // endregion

    // region setActiveChatId

    @Test
    fun whenSetActiveChatIdThenPublisherUpdatesChatIdForActiveTab() = runTest {
        val tabId = "tab-A"
        testee.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        testee.setActiveChatId("chat-123")
        advanceUntilIdle()

        assertEquals("chat-123", nativeInputStateProvider.stateForTab(tabId).value.chatId)
    }

    @Test
    fun whenSetActiveChatIdWithNullThenPublisherClearsChatId() = runTest {
        val tabId = "tab-A"
        testee.configure(tabId = tabId, isDuckAiMode = false, isBottom = false)
        testee.setActiveChatId("chat-123")
        advanceUntilIdle()

        testee.setActiveChatId(null)
        advanceUntilIdle()

        assertNull(nativeInputStateProvider.stateForTab(tabId).value.chatId)
    }

    @Test
    fun whenSetActiveChatIdBeforeConfigureThenChatIdIsBufferedAndPublishedOnConfigure() = runTest {
        // Defensive: setActiveChatId fires before activeTabId is set. The pending value must be
        // replayed once configure runs.
        val freshViewModel = createViewModel()

        freshViewModel.setActiveChatId("chat-123")
        advanceUntilIdle()

        freshViewModel.configure(tabId = "tab-A", isDuckAiMode = false, isBottom = false)
        advanceUntilIdle()

        assertEquals("chat-123", nativeInputStateProvider.stateForTab("tab-A").value.chatId)
    }

    // endregion

    // region fireChatHistorySelectedPixel

    @Test
    fun whenFireChatHistorySelectedPixelPinnedThenFiresPinnedPixels() {
        testee.fireChatHistorySelectedPixel(pinned = true)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_COUNT)
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_DAILY, type = Daily())
        verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_COUNT)
        verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_DAILY, type = Daily())
    }

    @Test
    fun whenFireChatHistorySelectedPixelNotPinnedThenFiresRegularPixels() {
        testee.fireChatHistorySelectedPixel(pinned = false)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_COUNT)
        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_DAILY, type = Daily())
        verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_COUNT)
        verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_RECENT_CHAT_SELECTED_PINNED_DAILY, type = Daily())
    }

    // endregion

    // region setToggleSelection

    @Test
    fun whenSetToggleSelectionThenStateReflectsTheSelection() = runTest {
        testee.setToggleSelection(NativeInputState.ToggleSelection.DUCK_AI)

        val emitted = testee.state.first()

        assertEquals(NativeInputState.ToggleSelection.DUCK_AI, emitted.toggleSelection)
    }

    @Test
    fun whenConfigureCalledThenToggleSelectionFallsBackToContextDefault() = runTest {
        testee.setToggleSelection(NativeInputState.ToggleSelection.DUCK_AI)
        testee.state.first { it.toggleSelection == NativeInputState.ToggleSelection.DUCK_AI }

        testee.configure(tabId = "test-tab", isDuckAiMode = false, isBottom = false)

        val emitted = testee.state.first()
        assertEquals(NativeInputState.ToggleSelection.SEARCH, emitted.toggleSelection)
    }

    // endregion
}
