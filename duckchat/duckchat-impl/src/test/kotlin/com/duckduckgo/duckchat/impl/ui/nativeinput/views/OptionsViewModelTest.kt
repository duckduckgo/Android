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
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
    private lateinit var testee: OptionsViewModel

    @Before
    fun setUp() {
        testee = OptionsViewModel(store, duckChatPixels)
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
        verify(duckChatPixels).fireImageGenerationSelected()
    }

    @Test
    fun whenWebSearchDeselectedByUserThenDeselectedPixel() {
        testee.onToolDeselectedByUser(Tool.WEB_SEARCH)
        verify(duckChatPixels).fireWebSearchDeselected()
    }

    @Test
    fun whenWebSearchSelectedByUserThenSelectedPixel() {
        testee.onToolSelectedByUser(Tool.WEB_SEARCH)
        verify(duckChatPixels).fireWebSearchSelected()
    }

    @Test
    fun whenImageGenDeselectedByUserThenDeselectedPixel() {
        testee.onToolDeselectedByUser(Tool.IMAGE_GENERATION)
        verify(duckChatPixels).fireImageGenerationDeselected()
    }

    @Test
    fun whenCustomizeResponsesClickedThenFireCustomizeResponsesPixel() {
        testee.onCustomizeResponsesClicked()
        verify(duckChatPixels).fireCustomizeResponsesSelected()
    }

    @Test
    fun whenVisibleToolsAutoClearsSelectedToolThenNoPixel() = runTest {
        val tabId = "tab-E"
        store.publish(tabId, NativeInputState.zero().copy(selectedTool = Tool.WEB_SEARCH.rawValue))
        selectedTabFlow.value = tabEntity(tabId)
        advanceUntilIdle()
        assertEquals(Tool.WEB_SEARCH, testee.selectedTool.value)

        // A model-capability change that removes the selected tool must auto-clear it
        // (updateVisibleTools returns true) WITHOUT firing a deselect pixel.
        val selectionCleared = testee.updateVisibleTools(emptySet())

        assertTrue(selectionCleared)
        verifyNoInteractions(duckChatPixels)
    }

    private fun tabEntity(tabId: String): TabEntity = TabEntity(tabId = tabId, position = 0)
}
