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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.nativeinput.PromptContribution
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
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

        testee = createViewModel()
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
        testee.configure(isDuckAiMode = true, isBottom = true)

        val state = testee.state.firstOrNull()!!
        assertEquals(NativeInputState.InputContext.DUCK_AI, state.inputContext)
        assertEquals(NativeInputState.InputPosition.BOTTOM, state.inputPosition)
    }

    @Test
    fun whenStorePendingPromptThenDelegatesToStoreWithModelId() = runTest {
        val plugin = fakePlugin(containerId = 1, modelId = "model-1")
        val viewModel = createViewModel(plugins = listOf(plugin))

        viewModel.storePendingPrompt("hello")

        verify(pendingNativePromptStore).store("hello", "model-1")
    }

    @Test
    fun whenStorePendingPromptWithNoPluginsThenModelIdIsNull() = runTest {
        val viewModel = createViewModel(plugins = emptyList())

        viewModel.storePendingPrompt("hello")

        verify(pendingNativePromptStore).store("hello", null)
    }

    @Test
    fun whenCancelChatSuggestionsThenDelegatesToReader() {
        testee.cancelChatSuggestions()

        verify(chatSuggestionsReader).tearDown()
    }

    @Test
    fun whenFetchChatSuggestionsThenReturnsReaderSuggestions() = runTest {
        val suggestions = listOf(
            ChatSuggestion(chatId = "id-1", title = "Title", lastEdit = LocalDateTime.now(), pinned = false),
        )
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenReturn(suggestions)

        val result = testee.fetchChatSuggestions("query")

        assertEquals(suggestions, result)
    }

    @Test
    fun whenFetchChatSuggestionsFailsThenReturnsEmptyList() = runTest {
        whenever(chatSuggestionsReader.fetchSuggestions(any())).thenAnswer { throw IOException("boom") }

        val result = testee.fetchChatSuggestions("query")

        assertTrue(result.isEmpty())
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
    fun whenFetchChatSuggestionsThenDelegatesQueryToReader() = runTest {
        whenever(chatSuggestionsReader.fetchSuggestions("hello world")).thenReturn(emptyList())

        testee.fetchChatSuggestions("hello world")

        verify(chatSuggestionsReader).fetchSuggestions("hello world")
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
            override fun createView(context: Context): View = View(context)
            override fun getPromptContribution(): PromptContribution? =
                modelId?.let { PromptContribution.ModelSelection(it) }
        }
    }
}
