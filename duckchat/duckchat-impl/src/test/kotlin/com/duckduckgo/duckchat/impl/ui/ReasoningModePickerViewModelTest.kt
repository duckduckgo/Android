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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.models.AvailableReasoningMode
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.ReasoningEffort
import com.duckduckgo.duckchat.impl.models.ReasoningMode
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ReasoningModePickerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReasoningModePickerViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val state = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(state)
    }

    private val testee = ReasoningModePickerViewModel(modelManager)

    @Test
    fun whenAvailableEmptyThenResolvedModeIsNull() {
        state.value = ModelState(availableReasoningModes = emptyList())
        assertNull(testee.resolvedMode(state.value))
    }

    @Test
    fun whenPersistedNullThenResolvedModeIsFirstAvailable() {
        state.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        assertEquals(ReasoningMode.FAST, testee.resolvedMode(state.value))
    }

    @Test
    fun whenPersistedSupportedThenResolvedModeIsPersisted() {
        state.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        assertEquals(ReasoningMode.REASONING, testee.resolvedMode(state.value))
    }

    @Test
    fun whenSelectModeThenDelegatesToManager() = runTest {
        testee.selectMode(ReasoningMode.REASONING)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
        verify(modelManager).selectReasoningMode(ReasoningMode.REASONING)
    }

    @Test
    fun whenIconResForEachModeThenReturnsExpectedDrawable() {
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_fast_24,
            testee.iconResFor(ReasoningMode.FAST),
        )
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_thinking_24,
            testee.iconResFor(ReasoningMode.REASONING),
        )
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_extended_24,
            testee.iconResFor(ReasoningMode.EXTENDED_REASONING),
        )
    }

    @Test
    fun whenRowsBuiltThenSelectedReflectsResolvedMode() {
        state.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        val rows = testee.rows(state.value)
        assertEquals(2, rows.size)
        assertEquals(ReasoningMode.FAST, rows[0].mode)
        assertEquals(ReasoningMode.REASONING, rows[1].mode)
        assertEquals(false, rows[0].selected)
        assertEquals(true, rows[1].selected)
    }
}
