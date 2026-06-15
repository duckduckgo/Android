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

class LogoOnlyContentTest {

    @Test
    fun `logo-only when the logo is visible and no hatch occupies space`() {
        // The NewTabReturnHatchView container is always VISIBLE and collapses to zero height when no
        // hatch is shown. A height of 0 must NOT count as a hatch - otherwise the logo would be
        // pinned below the input widget and shift vertically between the Search and Duck.ai tabs.
        assertTrue(isLogoOnly(logoVisible = true, hatchHeightPx = 0))
    }

    @Test
    fun `not logo-only when the logo is not visible`() {
        assertFalse(isLogoOnly(logoVisible = false, hatchHeightPx = 0))
    }

    @Test
    fun `not logo-only when a real hatch occupies space`() {
        assertFalse(isLogoOnly(logoVisible = true, hatchHeightPx = 120))
    }
}
