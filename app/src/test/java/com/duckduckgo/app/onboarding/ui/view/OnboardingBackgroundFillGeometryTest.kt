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

package com.duckduckgo.app.onboarding.ui.view

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingBackgroundFillGeometryTest {

    @Test
    fun whenWideImageFilledThenScaledByHeightAndRightAligned() {
        val t = endBottomFillTransform(viewWidth = 411, viewHeight = 280, drawableWidth = 1722, drawableHeight = 504)
        assertEquals(280f / 504f, t.scale, 0.0001f)
        assertEquals(411f - 1722f * (280f / 504f), t.translateX, 0.01f)
        assertEquals(0f, t.translateY, 0.01f)
    }

    @Test
    fun whenAnyDimensionIsZeroThenReturnsIdentity() {
        val t = endBottomFillTransform(viewWidth = 0, viewHeight = 280, drawableWidth = 1722, drawableHeight = 504)
        assertEquals(1f, t.scale, 0f)
        assertEquals(0f, t.translateX, 0f)
        assertEquals(0f, t.translateY, 0f)
    }

    @Test
    fun whenRequestedExceedsFractionOfAvailableThenCapped() {
        assertEquals(360, cappedFillHeightPx(requestedPx = 600, availableHeightPx = 900, maxHeightFraction = 0.4f))
    }

    @Test
    fun whenRequestedBelowFractionOfAvailableThenUnchanged() {
        assertEquals(300, cappedFillHeightPx(requestedPx = 300, availableHeightPx = 1600, maxHeightFraction = 0.4f))
    }

    @Test
    fun whenFractionAtLeastOneThenNoCap() {
        assertEquals(600, cappedFillHeightPx(requestedPx = 600, availableHeightPx = 900, maxHeightFraction = 1f))
    }

    @Test
    fun whenAvailableHeightNonPositiveThenNoCap() {
        assertEquals(600, cappedFillHeightPx(requestedPx = 600, availableHeightPx = 0, maxHeightFraction = 0.4f))
    }
}
