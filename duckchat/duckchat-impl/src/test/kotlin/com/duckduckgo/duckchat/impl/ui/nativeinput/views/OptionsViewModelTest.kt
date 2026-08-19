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
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModelProvider
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OptionsViewModelTest {

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
    private val duckChatPixels: DuckChatPixels = mock()
    private val modelStateFlow = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(modelStateFlow)
    }
    private val effectiveModelId = MutableStateFlow<String?>(null)
    private val effectiveModelProvider = object : EffectiveModelProvider {
        override val effectiveModelId: Flow<String?> = this@OptionsViewModelTest.effectiveModelId
        override fun onRecoveryModelPicked(modelId: String) = Unit
    }
    private lateinit var testee: OptionsViewModel

    @Before
    fun setUp() {
        testee = OptionsViewModel(store, duckChatPixels, modelManager, effectiveModelProvider)
    }

    @Test
    fun whenSelectedTabHasToolThenSelectedToolEmitsMatchingTool() = runTest {
        val tabId = "tab-A"
        store.publish(tabId, NativeInputState.zero().copy(selectedTool = Tool.WEB_SEARCH.rawValue))
        selectedTabFlow.value = tabEntity(tabId)
        advanceUntilIdle()

        assertEquals(Tool.WEB_SEARCH, testee.selectedTool.value)
    }

    @Test
    fun whenSelectedTabHasNoToolThenSelectedToolEmitsNull() = runTest {
        val tabId = "tab-B"
        store.publish(tabId, NativeInputState.zero())
        selectedTabFlow.value = tabEntity(tabId)
        advanceUntilIdle()

        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenImageGenerationSelectedOnSelectedTabThenShouldShowPickersIsFalse() = runTest {
        val tabId = "tab-D"
        store.publish(tabId, NativeInputState.zero().copy(selectedTool = Tool.IMAGE_GENERATION.rawValue))
        selectedTabFlow.value = tabEntity(tabId)
        advanceUntilIdle()

        assertFalse(testee.shouldShowPickers)
    }

    @Test
    fun whenSelectedTabsStateChangesThenSelectedToolReEmits() = runTest {
        val tabId = "tab-C"
        selectedTabFlow.value = tabEntity(tabId)
        advanceUntilIdle()
        assertNull(testee.selectedTool.value)

        store.update(tabId) { it.copy(selectedTool = Tool.IMAGE_GENERATION.rawValue) }
        advanceUntilIdle()

        assertEquals(Tool.IMAGE_GENERATION, testee.selectedTool.value)
    }

    @Test
    fun whenImageGenSelectedByUserThenSelectedPixel() {
        testee.onToolSelectedByUser(Tool.IMAGE_GENERATION)
        verify(duckChatPixels).fireImageGenerationSelected(any())
    }

    @Test
    fun whenWebSearchDeselectedByUserThenDeselectedPixel() {
        testee.onToolDeselectedByUser(Tool.WEB_SEARCH)
        verify(duckChatPixels).fireWebSearchDeselected(any())
    }

    @Test
    fun whenWebSearchSelectedByUserThenSelectedPixel() {
        testee.onToolSelectedByUser(Tool.WEB_SEARCH)
        verify(duckChatPixels).fireWebSearchSelected(any())
    }

    @Test
    fun whenImageGenDeselectedByUserThenDeselectedPixel() {
        testee.onToolDeselectedByUser(Tool.IMAGE_GENERATION)
        verify(duckChatPixels).fireImageGenerationDeselected(any())
    }

    @Test
    fun whenCustomizeResponsesClickedThenFireCustomizeResponsesPixel() {
        testee.onCustomizeResponsesClicked()
        verify(duckChatPixels).fireCustomizeResponsesSelected(any())
    }

    @Test
    fun whenSelectedToolStopsBeingSupportedThenSelectionClearedEmitsWithoutPixel() = runTest {
        val tabId = "tab-E"
        store.publish(tabId, NativeInputState.zero().copy(selectedTool = Tool.WEB_SEARCH.rawValue))
        selectedTabFlow.value = tabEntity(tabId)
        givenModels(model("m1", supportedTools = listOf(Tool.WEB_SEARCH)))
        effectiveModelId.value = "m1"
        advanceUntilIdle()
        assertEquals(Tool.WEB_SEARCH, testee.selectedTool.value)

        givenModels(model("m1", supportedTools = listOf(Tool.IMAGE_GENERATION)))
        advanceUntilIdle()

        assertEquals(Unit, testee.toolSelectionCleared.first())
        verifyNoInteractions(duckChatPixels)
    }

    @Test
    fun whenEffectiveModelSupportsOneToolThenOnlyThatToolIsVisible() = runTest {
        givenModels(model("m1", supportedTools = listOf(Tool.WEB_SEARCH)))
        effectiveModelId.value = "m1"
        advanceUntilIdle()

        assertEquals(setOf(Tool.WEB_SEARCH), testee.visibleTools.value)
    }

    @Test
    fun whenEffectiveModelIsUnknownThenAllToolsAreVisible() = runTest {
        givenModels(model("m1", supportedTools = listOf(Tool.WEB_SEARCH)))
        effectiveModelId.value = "not-in-the-list"
        advanceUntilIdle()

        assertEquals(Tool.entries.toSet(), testee.visibleTools.value)
    }

    @Test
    fun whenEffectiveModelChangesThenVisibleToolsFollowIt() = runTest {
        givenModels(
            model("m1", supportedTools = listOf(Tool.WEB_SEARCH)),
            model("m2", supportedTools = Tool.entries),
        )
        effectiveModelId.value = "m1"
        advanceUntilIdle()
        assertEquals(setOf(Tool.WEB_SEARCH), testee.visibleTools.value)

        effectiveModelId.value = "m2"
        advanceUntilIdle()

        assertEquals(Tool.entries.toSet(), testee.visibleTools.value)
    }

    private fun givenModels(vararg models: AIChatModel) {
        modelStateFlow.value = ModelState(models = models.toList(), selectedModelId = models.firstOrNull()?.id)
    }

    private fun model(id: String, supportedTools: List<Tool>): AIChatModel = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = emptyList(),
        isAccessible = true,
        supportedTools = supportedTools,
    )

    private fun tabEntity(tabId: String): TabEntity = TabEntity(tabId = tabId, position = 0)
}
