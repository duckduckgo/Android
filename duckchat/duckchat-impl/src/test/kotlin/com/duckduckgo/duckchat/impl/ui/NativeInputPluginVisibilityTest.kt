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

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowModelPicker
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowOptionsContainer
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowPluginControls
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowReasoningPicker
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowStopStreaming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputPluginVisibilityTest {

    @Test
    fun `plugin controls shown on duck ai tab when not streaming`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertTrue(state.shouldShowPluginControls())
    }

    @Test
    fun `plugin controls hidden on duck ai tab while streaming`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = true)
        assertFalse(state.shouldShowPluginControls())
    }

    @Test
    fun `plugin controls hidden on search tab when not streaming`() {
        val state = stateOf(ToggleSelection.SEARCH, isChatStreaming = false)
        assertFalse(state.shouldShowPluginControls())
    }

    @Test
    fun `plugin controls shown in duck ai context when not streaming`() {
        // In DUCK_AI context there is no toggle; toggleSelection defaults to DUCK_AI.
        // Plugin controls should show while not streaming.
        val state = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
            isChatStreaming = false,
        )
        assertTrue(state.shouldShowPluginControls())
    }

    @Test
    fun `plugin controls hidden in edit mode`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertFalse(state.shouldShowPluginControls(isEditing = true))
    }

    @Test
    fun `options container suppressed in edit mode even when tab state would show it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertFalse(shouldShowOptionsContainer(state, isEditMode = true))
    }

    @Test
    fun `options container shown outside edit mode when tab state allows it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertTrue(shouldShowOptionsContainer(state, isEditMode = false))
    }

    @Test
    fun `model picker suppressed in edit mode even when enabled with models and tab state would show it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertFalse(shouldShowModelPicker(state, pickerEnabled = true, hasModels = true, isEditMode = true))
    }

    @Test
    fun `model picker shown outside edit mode when enabled with models and tab state allows it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertTrue(shouldShowModelPicker(state, pickerEnabled = true, hasModels = true, isEditMode = false))
    }

    @Test
    fun `reasoning picker suppressed in edit mode even when visible and tab state would show it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertFalse(shouldShowReasoningPicker(state, pickerVisible = true, isEditMode = true))
    }

    @Test
    fun `reasoning picker shown outside edit mode when visible and tab state allows it`() {
        val state = stateOf(ToggleSelection.DUCK_AI, isChatStreaming = false)
        assertTrue(shouldShowReasoningPicker(state, pickerVisible = true, isEditMode = false))
    }

    @Test
    fun `stop streaming suppressed in edit mode even while streaming`() {
        assertFalse(shouldShowStopStreaming(streaming = true, isEditMode = true))
    }

    @Test
    fun `stop streaming shown outside edit mode while streaming`() {
        assertTrue(shouldShowStopStreaming(streaming = true, isEditMode = false))
    }

    @Test
    fun `stop streaming hidden outside edit mode when not streaming`() {
        assertFalse(shouldShowStopStreaming(streaming = false, isEditMode = false))
    }

    private fun stateOf(
        toggleSelection: ToggleSelection,
        isChatStreaming: Boolean,
    ) = NativeInputState(
        inputMode = InputMode.SEARCH_AND_DUCK_AI,
        inputContext = InputContext.BROWSER,
        toggleSelection = toggleSelection,
        isChatStreaming = isChatStreaming,
    )
}
