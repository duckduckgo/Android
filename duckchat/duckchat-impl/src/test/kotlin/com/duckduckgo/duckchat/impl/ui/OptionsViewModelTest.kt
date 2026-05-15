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

import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.OptionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OptionsViewModelTest {

    private lateinit var testee: OptionsViewModel

    @Before
    fun setUp() {
        testee = OptionsViewModel()
    }

    @Test
    fun whenCreatedThenNoToolSelected() {
        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenCreatedThenAllToolsVisible() {
        assertEquals(Tool.entries.toSet(), testee.visibleTools.value)
    }

    @Test
    fun whenCreatedThenShouldShowPickersIsTrue() {
        assertTrue(testee.shouldShowPickers)
    }

    @Test
    fun whenToolToggledThenItBecomesSelected() {
        testee.toggleTool(Tool.WEB_SEARCH)

        assertEquals(Tool.WEB_SEARCH, testee.selectedTool.value)
    }

    @Test
    fun whenSelectedToolToggledAgainThenSelectionIsCleared() {
        testee.toggleTool(Tool.WEB_SEARCH)
        testee.toggleTool(Tool.WEB_SEARCH)

        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenDifferentToolToggledThenSelectionSwitches() {
        testee.toggleTool(Tool.WEB_SEARCH)
        testee.toggleTool(Tool.IMAGE_GENERATION)

        assertEquals(Tool.IMAGE_GENERATION, testee.selectedTool.value)
    }

    @Test
    fun whenToolClearedThenNoToolSelected() {
        testee.toggleTool(Tool.WEB_SEARCH)
        testee.clearTool()

        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenToolClearedWithNoSelectionThenNoToolSelected() {
        testee.clearTool()

        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenNoToolSelectedThenShouldShowPickersIsTrue() {
        assertTrue(testee.shouldShowPickers)
    }

    @Test
    fun whenWebSearchSelectedThenShouldShowPickersIsTrue() {
        testee.toggleTool(Tool.WEB_SEARCH)

        assertTrue(testee.shouldShowPickers)
    }

    @Test
    fun whenImageGenerationSelectedThenShouldShowPickersIsFalse() {
        testee.toggleTool(Tool.IMAGE_GENERATION)

        assertFalse(testee.shouldShowPickers)
    }

    @Test
    fun whenImageGenerationClearedThenShouldShowPickersIsTrue() {
        testee.toggleTool(Tool.IMAGE_GENERATION)
        testee.clearTool()

        assertTrue(testee.shouldShowPickers)
    }

    @Test
    fun whenVisibleToolsUpdatedThenVisibleToolsReflectNewSet() {
        testee.updateVisibleTools(setOf(Tool.WEB_SEARCH))

        assertEquals(setOf(Tool.WEB_SEARCH), testee.visibleTools.value)
    }

    @Test
    fun whenVisibleToolsUpdatedAndNoSelectionThenReturnsFalse() {
        val selectionCleared = testee.updateVisibleTools(setOf(Tool.WEB_SEARCH))

        assertFalse(selectionCleared)
    }

    @Test
    fun whenVisibleToolsUpdatedAndSelectionStillSupportedThenReturnsFalse() {
        testee.toggleTool(Tool.WEB_SEARCH)

        val selectionCleared = testee.updateVisibleTools(setOf(Tool.WEB_SEARCH))

        assertFalse(selectionCleared)
        assertEquals(Tool.WEB_SEARCH, testee.selectedTool.value)
    }

    @Test
    fun whenVisibleToolsUpdatedAndSelectionNoLongerSupportedThenReturnsTrue() {
        testee.toggleTool(Tool.IMAGE_GENERATION)

        val selectionCleared = testee.updateVisibleTools(setOf(Tool.WEB_SEARCH))

        assertTrue(selectionCleared)
    }

    @Test
    fun whenSelectionClearedByUpdateVisibleToolsThenSelectedToolIsNull() {
        testee.toggleTool(Tool.IMAGE_GENERATION)

        testee.updateVisibleTools(setOf(Tool.WEB_SEARCH))

        assertNull(testee.selectedTool.value)
    }

    @Test
    fun whenVisibleToolsUpdatedWithEmptySetAndToolSelectedThenSelectionCleared() {
        testee.toggleTool(Tool.WEB_SEARCH)

        val selectionCleared = testee.updateVisibleTools(emptySet())

        assertTrue(selectionCleared)
        assertNull(testee.selectedTool.value)
    }
}
