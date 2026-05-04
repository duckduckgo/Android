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

import com.duckduckgo.duckchat.impl.ui.NativeInputState.InputContext
import com.duckduckgo.duckchat.impl.ui.NativeInputState.InputMode
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowCardRowBack
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowToggleRowBack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetBackButtonsTest {

    @Test
    fun `toggle row back is shown when toggle visible and context is browser`() {
        val state = stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER)

        assertTrue(state.shouldShowToggleRowBack())
        assertFalse(state.shouldShowCardRowBack())
    }

    @Test
    fun `toggle row back is hidden when toggle visible and context is duck ai`() {
        val state = stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI)

        assertFalse(state.shouldShowToggleRowBack())
        assertFalse(state.shouldShowCardRowBack())
    }

    @Test
    fun `toggle row back is hidden when toggle hidden and context is browser`() {
        val state = stateOf(InputMode.SEARCH_ONLY, InputContext.BROWSER)

        assertFalse(state.shouldShowToggleRowBack())
    }

    @Test
    fun `toggle row back is hidden when toggle hidden and context is duck ai`() {
        val state = stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI)

        assertFalse(state.shouldShowToggleRowBack())
    }

    @Test
    fun `card row back is shown when toggle hidden and context is browser`() {
        val state = stateOf(InputMode.SEARCH_ONLY, InputContext.BROWSER)

        assertTrue(state.shouldShowCardRowBack())
    }

    @Test
    fun `card row back is hidden when toggle hidden and context is duck ai`() {
        val state = stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI)

        assertFalse(state.shouldShowCardRowBack())
    }

    @Test
    fun `card row back is hidden when toggle visible regardless of context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER).shouldShowCardRowBack())
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI).shouldShowCardRowBack())
    }

    @Test
    fun `toggle row back is hidden in duck ai contextual context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL).shouldShowToggleRowBack())
        assertFalse(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI_CONTEXTUAL).shouldShowToggleRowBack())
    }

    @Test
    fun `card row back is hidden in duck ai contextual context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL).shouldShowCardRowBack())
        assertFalse(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI_CONTEXTUAL).shouldShowCardRowBack())
    }

    private fun stateOf(inputMode: InputMode, inputContext: InputContext): NativeInputState =
        NativeInputState(inputMode = inputMode, inputContext = inputContext)
}
