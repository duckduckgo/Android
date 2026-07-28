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

package com.duckduckgo.app.browser.nativeinput

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineNavButtonsDecisionTest {

    private fun decide(
        isDuckAiMode: Boolean = false,
        inputMode: InputMode = InputMode.SEARCH_ONLY,
        openedEmpty: Boolean = true,
        interactionLatched: Boolean = false,
    ) = shouldShowInlineNavButtons(isDuckAiMode, inputMode, openedEmpty, interactionLatched)

    @Test
    fun `shown for empty first-focus search-only`() = assertTrue(decide())

    @Test
    fun `hidden in Duck ai mode`() = assertFalse(decide(isDuckAiMode = true))

    @Test
    fun `hidden in Search and Duck ai mode`() = assertFalse(decide(inputMode = InputMode.SEARCH_AND_DUCK_AI))

    @Test
    fun `hidden when opened with prefilled text`() = assertFalse(decide(openedEmpty = false))

    @Test
    fun `hidden once the interaction latch is set`() = assertFalse(decide(interactionLatched = true))
}
