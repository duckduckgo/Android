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
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.nativeinput.PromptContribution
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private val nativeInputStatePublisher: NativeInputStatePublisher = mock()

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
            appCoroutineScope = TestScope(coroutineRule.testDispatcher),
        )
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

        assertEquals(NativeInputState.ToggleSelection.DUCK_AI, testee.state.firstOrNull()!!.defaultToggleSelection)
    }

    @Test
    fun whenContextIsBrowserThenDefaultToggleSelectionIsSearch() = runTest {
        assertEquals(NativeInputState.ToggleSelection.SEARCH, testee.state.firstOrNull()!!.defaultToggleSelection)
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

        assertEquals(NativeInputState.ToggleSelection.DUCK_AI, testee.state.firstOrNull()!!.defaultToggleSelection)
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
        val plugin = fakePlugin(containerId = 1, modelId = "model-1")
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
    fun whenBuildChatSuggestionUrlThenAppendsChatIdParam() {
        whenever(duckChatInternal.getDuckChatUrl("", false)).thenReturn("https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5")
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
        val plugin = fakePlugin(containerId = 42, modelId = "gpt-4o")
        val viewModel = createViewModel(plugins = listOf(plugin))

        val plugins = viewModel.plugins.value
        assertEquals(1, plugins.size)
        assertEquals(42, plugins[0].containerId)
    }

    @Test
    fun whenNoPluginsThenGetSelectedModelIdReturnsNull() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenPluginReturnsModelSelectionThenGetSelectedModelIdReturnsIt() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "claude-3")
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertEquals("claude-3", viewModel.getSelectedModelId())
    }

    @Test
    fun whenPluginReturnsNullContributionThenGetSelectedModelIdReturnsNull() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = null)
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelPickerDisabledThenGetSelectedModelIdReturnsNull() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "claude-3")
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.setModelPickerEnabled(false)

        assertNull(viewModel.getSelectedModelId())
    }

    @Test
    fun whenModelPickerReEnabledThenGetSelectedModelIdReturnsSelection() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "claude-3")
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.setModelPickerEnabled(false)
        assertNull(viewModel.getSelectedModelId())

        viewModel.setModelPickerEnabled(true)
        assertEquals("claude-3", viewModel.getSelectedModelId())
    }

    @Test
    fun whenPluginReturnsReasoningEffortSelectionThenGetResolvedReasoningEffortReturnsIt() = runTest {
        val plugin = fakeReasoningPlugin(containerId = 7, effort = "low")
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertEquals("low", viewModel.getResolvedReasoningEffort())
    }

    @Test
    fun whenNoPluginContributesReasoningEffortThenGetResolvedReasoningEffortReturnsNull() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "claude-3")
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertNull(viewModel.getResolvedReasoningEffort())
    }

    @Test
    fun whenNoPluginsThenGetSelectedToolReturnsNull() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        assertNull(viewModel.getSelectedTool())
    }

    @Test
    fun whenPluginReturnsToolSelectionThenGetSelectedToolReturnsIt() = runTest {
        val plugin = fakeToolPlugin(containerId = 3, tool = "WebSearch")
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertEquals("WebSearch", viewModel.getSelectedTool())
    }

    @Test
    fun whenNoPluginContributesToolSelectionThenGetSelectedToolReturnsNull() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "claude-3")
        val viewModel = createViewModel(plugins = listOf(plugin))

        assertNull(viewModel.getSelectedTool())
    }

    @Test
    fun whenStorePendingPromptWithSelectedToolThenForwardsTool() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        viewModel.storePendingPrompt("hello", "model-1", null, selectedTool = "GenerateImage")

        verify(pendingNativePromptStore).store("hello", "model-1", null, "GenerateImage", emptyList(), emptyList())
    }

    private fun fakeReasoningPlugin(containerId: Int, effort: String?): NativeInputPlugin {
        return object : NativeInputPlugin {
            override val containerId: Int = containerId
            override fun createView(context: Context, host: NativeInputHost): View = View(context)
            override fun getPromptContribution(): PromptContribution? =
                effort?.let { PromptContribution.ReasoningEffortSelection(it) }
        }
    }

    private fun fakeToolPlugin(containerId: Int, tool: String?): NativeInputPlugin {
        return object : NativeInputPlugin {
            override val containerId: Int = containerId
            override fun createView(context: Context, host: NativeInputHost): View = View(context)
            override fun getPromptContribution(): PromptContribution? =
                tool?.let { PromptContribution.ToolSelection(it) }
        }
    }

    @Test
    fun whenUpdatePluginContainerVisibilityThenSendsCommand() = runTest {
        val plugin = fakePlugin(containerId = 99, modelId = null)
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.updatePluginContainerVisibility(isChatTab = true)

        val command = viewModel.commands.firstOrNull()
        assertTrue(command is NativeInputModeWidgetViewModel.Command.UpdatePluginVisibility)
        val update = command as NativeInputModeWidgetViewModel.Command.UpdatePluginVisibility
        assertEquals(listOf(99), update.containerIds)
        assertTrue(update.visible)
    }

    private fun fakePlugin(containerId: Int, modelId: String?): NativeInputPlugin {
        return object : NativeInputPlugin {
            override val containerId: Int = containerId
            override fun createView(context: Context, host: NativeInputHost): View = View(context)
            override fun getPromptContribution(): PromptContribution? =
                modelId?.let { PromptContribution.ModelSelection(it) }
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
}
