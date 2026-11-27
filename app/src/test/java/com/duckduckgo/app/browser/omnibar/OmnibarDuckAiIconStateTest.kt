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

package com.duckduckgo.app.browser.omnibar

import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmnibarDuckAiIconStateTest {

    @Test
    fun whenBrowserModeThenContextualSheetIconShown() {
        val state = ViewState(viewMode = ViewMode.Browser(null))
        assertTrue(state.showContextualSheetIcon)
    }

    @Test
    fun whenDuckAiModeThenContextualSheetIconShown() {
        val state = ViewState(viewMode = ViewMode.DuckAI)
        assertTrue(state.showContextualSheetIcon)
    }

    @Test
    fun whenNewTabModeThenContextualSheetIconHidden() {
        // On the new-tab page we always show the full-screen Duck.ai entry point.
        val state = ViewState(viewMode = ViewMode.NewTab)
        assertFalse(state.showContextualSheetIcon)
    }

    @Test
    fun whenSearchOnlyThenContextualSheetIconHidden() {
        val state = ViewState(isSearchOnly = true, viewMode = ViewMode.Browser(null))
        assertFalse(state.showContextualSheetIcon)
    }
}
