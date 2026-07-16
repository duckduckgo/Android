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

class NavBarVisibilityDecisionTest {

    @Test
    fun `nav bar is created only with the flag on, in browser, and Search and Duck ai mode`() {
        assertTrue(shouldCreateNavBar(featureEnabled = true, isDuckAiMode = false, inputMode = InputMode.SEARCH_AND_DUCK_AI))
    }

    @Test
    fun `nav bar is not created when the flag is off`() {
        assertFalse(shouldCreateNavBar(featureEnabled = false, isDuckAiMode = false, inputMode = InputMode.SEARCH_AND_DUCK_AI))
    }

    @Test
    fun `nav bar is not created in search-only mode`() {
        assertFalse(shouldCreateNavBar(featureEnabled = true, isDuckAiMode = false, inputMode = InputMode.SEARCH_ONLY))
    }

    @Test
    fun `nav bar is not created in Duck ai conversation mode`() {
        assertFalse(shouldCreateNavBar(featureEnabled = true, isDuckAiMode = true, inputMode = InputMode.SEARCH_AND_DUCK_AI))
    }

    @Test
    fun `nav bar shows in browser context until the user interacts`() {
        assertTrue(shouldShowNavBar(isBrowserContext = true, interactionLatched = false))
    }

    @Test
    fun `nav bar hides once the interaction latch is set`() {
        assertFalse(shouldShowNavBar(isBrowserContext = true, interactionLatched = true))
    }

    @Test
    fun `nav bar never shows outside the browser context`() {
        assertFalse(shouldShowNavBar(isBrowserContext = false, interactionLatched = false))
        assertFalse(shouldShowNavBar(isBrowserContext = false, interactionLatched = true))
    }

    @Test
    fun `animate whenever the target differs from the current shown state`() {
        // Unknown current state (first apply) always animates/applies.
        assertTrue(shouldAnimateNavBar(currentShown = null, targetShown = true))
        assertTrue(shouldAnimateNavBar(currentShown = null, targetShown = false))
        // Transitions apply.
        assertTrue(shouldAnimateNavBar(currentShown = false, targetShown = true))
        assertTrue(shouldAnimateNavBar(currentShown = true, targetShown = false))
        // No-ops when already in the target state.
        assertFalse(shouldAnimateNavBar(currentShown = true, targetShown = true))
        assertFalse(shouldAnimateNavBar(currentShown = false, targetShown = false))
    }
}
