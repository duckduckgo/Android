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

package com.duckduckgo.app.onboarding.ui.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingDecorationSizingTest {

    @Test
    fun availableAboveMax_returnsMax() {
        assertEquals(274, OnboardingDecorationSizing.fitHeight(availablePx = 500, minHeightPx = 174, maxHeightPx = 274))
    }

    @Test
    fun availableBetweenMinAndMax_returnsAvailable() {
        assertEquals(200, OnboardingDecorationSizing.fitHeight(availablePx = 200, minHeightPx = 174, maxHeightPx = 274))
    }

    @Test
    fun availableBelowMin_returnsNull() {
        assertNull(OnboardingDecorationSizing.fitHeight(availablePx = 100, minHeightPx = 174, maxHeightPx = 274))
    }

    @Test
    fun availableNegative_returnsNull() {
        assertNull(OnboardingDecorationSizing.fitHeight(availablePx = -50, minHeightPx = 174, maxHeightPx = 274))
    }
}
