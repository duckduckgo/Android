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
        assertEquals(NAV_BAR, contentTopInset(isBottom = true, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = 0, logoOverlapPx = 0))
    }

    @Test
    fun `bottom mode logo-only content is not inset`() {
        assertEquals(0, contentTopInset(isBottom = true, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = 0, logoOverlapPx = 0))
    }

    @Test
    fun `top mode insets content below the widget which already clears the nav bar`() {
        assertEquals(
            WIDGET_OFFSET,
            contentTopInset(isBottom = false, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = WIDGET_OFFSET, logoOverlapPx = 0),
        )
    }

    @Test
    fun `top mode logo-only content is not inset when the widget clears the logo`() {
        val overlap = logoOverlap(anchorBottomInWindow = WIDGET_BOTTOM, logoTopInWindow = WIDGET_BOTTOM + 100, gapPx = GAP)
        assertEquals(
            0,
            contentTopInset(isBottom = false, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = WIDGET_OFFSET, logoOverlapPx = overlap),
        )
    }

    @Test
    fun `top mode logo-only content moves down just enough to clear a widget that covers the logo`() {
        val logoTop = WIDGET_BOTTOM - 200
        val overlap = logoOverlap(anchorBottomInWindow = WIDGET_BOTTOM, logoTopInWindow = logoTop, gapPx = GAP)
        val inset =
            contentTopInset(isBottom = false, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = WIDGET_OFFSET, logoOverlapPx = overlap)

        assertEquals(WIDGET_BOTTOM + GAP, logoTop + inset)
    }

    @Test
    fun `bottom mode logo-only content ignores the logo overlap`() {
        assertEquals(0, contentTopInset(isBottom = true, isLogoOnly = true, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = 0, logoOverlapPx = 200))
    }

    @Test
    fun `top mode clamps a negative widget offset to zero`() {
        assertEquals(0, contentTopInset(isBottom = false, isLogoOnly = false, navBarInsetPx = NAV_BAR, widgetTopOffsetPx = -20, logoOverlapPx = 0))
    }

    @Test
    fun `top mode content anchor includes visible footer height`() {
        assertEquals(
            WIDGET_BOTTOM + FOOTER_HEIGHT,
            contentAnchorBottom(
                isBottom = false,
                widgetBottomInWindow = WIDGET_BOTTOM,
                visibleFooterExtent = FOOTER_HEIGHT,
            ),
        )
    }

    @Test
    fun `bottom mode content anchor excludes footer height`() {
        assertEquals(
            WIDGET_BOTTOM,
            contentAnchorBottom(
                isBottom = true,
                widgetBottomInWindow = WIDGET_BOTTOM,
                visibleFooterExtent = FOOTER_HEIGHT,
            ),
        )
    }

    private companion object {
        private const val NAV_BAR = 56
        private const val WIDGET_OFFSET = 120
        private const val WIDGET_BOTTOM = 300
        private const val FOOTER_HEIGHT = 50
        private const val GAP = 16
    }
}
