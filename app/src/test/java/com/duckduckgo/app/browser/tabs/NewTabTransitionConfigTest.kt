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

package com.duckduckgo.app.browser.tabs

import com.duckduckgo.app.browser.tabs.NewTabOrigin.BELOW_CENTER
import com.duckduckgo.app.browser.tabs.NewTabOrigin.RIGHT_OF_CENTER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTabTransitionConfigTest {

    private val default = NewTabTransitionConfig.DEFAULT

    @Test
    fun whenDefaultRightOfCenterThenMotionMatchesArtifact() {
        assertEquals(
            NewTabMotion(
                startScale = 0.8f,
                offsetXFraction = 0.25f,
                offsetYFraction = 0f,
                backgroundScale = 0.95f,
                dimAlpha = 0.25f,
                slideLeftFraction = 0.25f,
                durationMs = 360L,
            ),
            default.motionFor(RIGHT_OF_CENTER),
        )
    }

    @Test
    fun whenDefaultBelowCenterThenOffsetIsVerticalAndDoesNotSlide() {
        val motion = default.motionFor(BELOW_CENTER)

        assertEquals(0f, motion.offsetXFraction)
        assertEquals(0.15f, motion.offsetYFraction)
        assertEquals(0f, motion.slideLeftFraction)
    }

    @Test
    fun whenBelowOffsetNegativeThenStartsAboveCenter() {
        val config = default.copy(belowCenter = default.belowCenter.copy(offsetPercent = -10))

        assertEquals(-0.1f, config.motionFor(BELOW_CENTER).offsetYFraction)
    }

    @Test
    fun whenDefaultThenTabCloseAnimates() {
        assertTrue(default.animateClose)
    }

    @Test
    fun whenSlowMotionThenDurationTimesFour() {
        val config = default.copy(slowMotion = true)

        assertEquals(1440L, config.motionFor(RIGHT_OF_CENTER).durationMs)
    }

    @Test
    fun whenBelowCenterSlideLeftSetThenStillDoesNotSlide() {
        val config = default.copy(belowCenter = default.belowCenter.copy(slideLeftPercent = 20))

        assertEquals(0f, config.motionFor(BELOW_CENTER).slideLeftFraction)
    }

    @Test
    fun whenRightOfCenterSlideLeftSetThenBelowCenterDoesNotSlide() {
        val config = default.copy(rightOfCenter = default.rightOfCenter.copy(slideLeftPercent = 20))

        assertEquals(0.2f, config.motionFor(RIGHT_OF_CENTER).slideLeftFraction)
        assertEquals(0f, config.motionFor(BELOW_CENTER).slideLeftFraction)
    }
}
