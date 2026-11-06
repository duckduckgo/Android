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
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.usesExpandedVerticalPadding
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputVerticalPaddingTest {

    @Test
    fun `browser omnibar with toggle is expanded when focused`() {
        assertTrue(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER), isFocused = true))
    }

    @Test
    fun `browser omnibar with toggle stays expanded when focus is lost`() {
        assertTrue(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.BROWSER), isFocused = false))
    }

    @Test
    fun `browser omnibar without toggle stays minimized when focused`() {
        assertFalse(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_ONLY, InputContext.BROWSER), isFocused = true))
    }

    @Test
    fun `duck ai omnibar is expanded when focused`() {
        assertTrue(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI), isFocused = true))
    }

    @Test
    fun `duck ai omnibar is minimized when not focused`() {
        assertFalse(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI), isFocused = false))
    }

    @Test
    fun `contextual sheet is expanded when focused`() {
        assertTrue(usesExpandedVerticalPadding(stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL), isFocused = true))
    }

    @Test
    fun `contextual sheet is minimized when not focused`() {
        assertFalse(
            usesExpandedVerticalPadding(
                stateOf(InputMode.SEARCH_AND_DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL),
                isFocused = false,
            ),
        )
    }

    @Test
    fun `missing state is minimized`() {
        assertFalse(usesExpandedVerticalPadding(state = null, isFocused = true))
    }

    private fun stateOf(inputMode: InputMode, inputContext: InputContext) = NativeInputState(
        inputMode = inputMode,
        inputContext = inputContext,
    )
}
