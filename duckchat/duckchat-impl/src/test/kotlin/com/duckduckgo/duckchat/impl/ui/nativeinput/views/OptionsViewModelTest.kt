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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OptionsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val tabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(selectedTabFlow)
    }
    private val store = RealNativeInputStateStore { tabRepository }
    private lateinit var testee: OptionsViewModel

    @Before
    fun setUp() {
        testee = OptionsViewModel(store)
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

    private fun tabEntity(tabId: String): TabEntity = TabEntity(tabId = tabId, position = 0)
}
