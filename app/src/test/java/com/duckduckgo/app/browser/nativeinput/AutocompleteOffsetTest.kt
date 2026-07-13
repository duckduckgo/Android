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

import org.junit.Assert.assertEquals
import org.junit.Test

class AutocompleteOffsetTest {

    @Test
    fun `bottom mode offsets the list below the nav bar when the bar is shown`() {
        assertEquals(168, autocompleteTopOffset(isBottom = true, widgetVisualBottomPx = 640, autoCompleteListTopPx = 304, navBarInsetPx = 168))
    }

    @Test
    fun `bottom mode does not offset the list when the nav bar is hidden`() {
        assertEquals(0, autocompleteTopOffset(isBottom = true, widgetVisualBottomPx = 640, autoCompleteListTopPx = 304, navBarInsetPx = 0))
    }

    @Test
    fun `top mode ignores the nav bar inset and offsets below the widget's visual bottom`() {
        assertEquals(336, autocompleteTopOffset(isBottom = false, widgetVisualBottomPx = 640, autoCompleteListTopPx = 304, navBarInsetPx = 168))
    }

    @Test
    fun `top mode uses the visual bottom so a widget ridden up by the nav bar hide shrinks the offset`() {
        // Nav bar (168px) hidden: widget layout bottom 640 rides up via translationY -168 → visual bottom 472.
        // Using the layout bottom (640) would leave a 168px gap; the visual bottom removes it.
        assertEquals(168, autocompleteTopOffset(isBottom = false, widgetVisualBottomPx = 472, autoCompleteListTopPx = 304, navBarInsetPx = 0))
    }

    @Test
    fun `top mode clamps a negative offset to zero when the widget sits above the list`() {
        assertEquals(0, autocompleteTopOffset(isBottom = false, widgetVisualBottomPx = 100, autoCompleteListTopPx = 304, navBarInsetPx = 0))
    }

    @Test
    fun `top mode does not offset the list from the bottom`() {
        assertEquals(0, autocompleteBottomOffset(isBottom = false, autoCompleteListBottomPx = 1447, widgetTopPx = 640))
    }

    @Test
    fun `bottom mode offsets the list above the widget's top`() {
        assertEquals(307, autocompleteBottomOffset(isBottom = true, autoCompleteListBottomPx = 1447, widgetTopPx = 1140))
    }

    @Test
    fun `bottom mode clamps a negative bottom offset to zero`() {
        assertEquals(0, autocompleteBottomOffset(isBottom = true, autoCompleteListBottomPx = 1000, widgetTopPx = 1140))
    }
}
