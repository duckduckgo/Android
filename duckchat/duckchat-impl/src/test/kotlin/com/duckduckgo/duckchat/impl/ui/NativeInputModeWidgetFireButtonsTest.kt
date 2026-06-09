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
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowLeadingFireButton
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowTrailingFireButton
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetFireButtonsTest {

    @Test
    fun `leading fire is shown in duck ai context`() {
        assertTrue(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI).shouldShowLeadingFireButton())
        assertTrue(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI).shouldShowLeadingFireButton())
    }

    @Test
    fun `leading fire is hidden in browser context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER).shouldShowLeadingFireButton())
        assertFalse(stateOf(InputMode.SEARCH_ONLY, InputContext.BROWSER).shouldShowLeadingFireButton())
    }

    @Test
    fun `leading fire is hidden in duck ai contextual context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL).shouldShowLeadingFireButton())
        assertFalse(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI_CONTEXTUAL).shouldShowLeadingFireButton())
    }

    @Test
    fun `trailing fire is hidden in duck ai context`() {
        assertFalse(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI).shouldShowTrailingFireButton())
        assertFalse(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI).shouldShowTrailingFireButton())
    }

    @Test
    fun `trailing fire is shown in browser context`() {
        assertTrue(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER).shouldShowTrailingFireButton())
        assertTrue(stateOf(InputMode.SEARCH_ONLY, InputContext.BROWSER).shouldShowTrailingFireButton())
    }

    @Test
    fun `trailing fire is shown in duck ai contextual context`() {
        assertTrue(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL).shouldShowTrailingFireButton())
        assertTrue(stateOf(InputMode.SEARCH_ONLY, InputContext.DUCK_AI_CONTEXTUAL).shouldShowTrailingFireButton())
    }

    private fun stateOf(inputMode: InputMode, inputContext: InputContext): NativeInputState =
        NativeInputState(inputMode = inputMode, inputContext = inputContext)
}
