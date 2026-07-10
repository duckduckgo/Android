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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarVisibilityDecisionTest {

    @Test
    fun `nav bar shows only in browser context with an empty field`() {
        assertTrue(shouldShowNavBar(isBrowserContext = true, isInputEmpty = true))
        assertFalse(shouldShowNavBar(isBrowserContext = true, isInputEmpty = false))
        assertFalse(shouldShowNavBar(isBrowserContext = false, isInputEmpty = true))
        assertFalse(shouldShowNavBar(isBrowserContext = false, isInputEmpty = false))
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
