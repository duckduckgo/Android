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

class ContentTopInsetTest {

    @Test
    fun `bottom mode insets content by the nav bar height so it clears the top nav bar`() {
        assertEquals(NAV_BAR, contentTopInset(isBottom = true, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = 0))
    }

    @Test
    fun `bottom mode logo-only content is not inset`() {
        assertEquals(0, contentTopInset(isBottom = true, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = 0))
    }

    @Test
    fun `top mode insets content below the widget which already clears the nav bar`() {
        assertEquals(WIDGET_OFFSET, contentTopInset(isBottom = false, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = WIDGET_OFFSET))
    }

    @Test
    fun `top mode logo-only content is not inset`() {
        assertEquals(0, contentTopInset(isBottom = false, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = WIDGET_OFFSET))
    }

    @Test
    fun `top mode clamps a negative widget offset to zero`() {
        assertEquals(0, contentTopInset(isBottom = false, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = -20))
    }

    private companion object {
        private const val NAV_BAR = 56
        private const val WIDGET_OFFSET = 120
    }
}
