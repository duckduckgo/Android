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

package com.duckduckgo.app.browser.duckchat

import com.duckduckgo.app.browser.duckchat.SwipeUpNewChatIndicator.Companion.columnTranslation
import com.duckduckgo.app.browser.duckchat.SwipeUpNewChatIndicator.Companion.pullOffset
import com.duckduckgo.app.browser.duckchat.SwipeUpNewChatIndicator.Companion.pullStep
import com.duckduckgo.app.browser.duckchat.SwipeUpNewChatIndicator.Companion.ringProgress
import com.duckduckgo.app.browser.duckchat.SwipeUpNewChatIndicator.Companion.startsNewChat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeUpNewChatIndicatorTest {

    @Test
    fun whenPullStartsThenChatAlreadyLagsTheFinger() {
        val offset = pullOffset(distancePx = 100, limitPx = 600f)

        assertTrue(offset > 0f && offset < 60f)
    }

    @Test
    fun whenPullingFurtherThenEachStretchMovesTheChatLess() {
        val first = pullOffset(distancePx = 100, limitPx = 600f)
        val second = pullOffset(distancePx = 200, limitPx = 600f) - first

        assertTrue(second < first)
    }

    @Test
    fun whenPulledVeryFarThenChatNeverPassesTheLimit() {
        assertTrue(pullOffset(distancePx = 100_000, limitPx = 600f) < 600f)
    }

    @Test
    fun whenFingerTravelsAbout245ThenChatReachesTheArmOffset() {
        assertEquals(110f, pullOffset(distancePx = 245, limitPx = 600f), 1f)
    }

    @Test
    fun whenNotPulledThenStepIsZero() {
        assertEquals(0, pullStep(offsetPx = 0f, armOffsetPx = 110f))
    }

    @Test
    fun whenPulledPartwayThenStepCountsFifthsOfTheArmOffset() {
        assertEquals(1, pullStep(offsetPx = 22f, armOffsetPx = 110f))
        assertEquals(4, pullStep(offsetPx = 109f, armOffsetPx = 110f))
    }

    @Test
    fun whenChatReachesOrPassesArmOffsetThenLastStepArms() {
        assertEquals(5, pullStep(offsetPx = 110f, armOffsetPx = 110f))
        assertEquals(5, pullStep(offsetPx = 200f, armOffsetPx = 110f))
    }

    @Test
    fun whenCircleNotYetOutFromUnderTheChatThenRingIsEmpty() {
        assertEquals(0f, ringProgress(offsetPx = 40f, armOffsetPx = 150f, revealedPx = 52f), 0f)
    }

    @Test
    fun whenCircleIsOutThenRingFillsTowardTrigger() {
        assertEquals(0.5f, ringProgress(offsetPx = 101f, armOffsetPx = 150f, revealedPx = 52f), 0.001f)
        assertEquals(1f, ringProgress(offsetPx = 180f, armOffsetPx = 150f, revealedPx = 52f), 0f)
    }

    @Test
    fun whenGapIsSmallerThanIndicatorThenItHangsBelowTheChatEdge() {
        // Indicator top sits 4px under the chat's edge; the rest is clipped below the gap.
        assertEquals(74f, columnTranslation(offsetPx = 10f, columnHeightPx = 80f, spacingPx = 4f), 0f)
    }

    @Test
    fun whenGapIsLargerThanIndicatorThenItIsCentredInTheGap() {
        assertEquals(-35f, columnTranslation(offsetPx = 150f, columnHeightPx = 80f, spacingPx = 4f), 0f)
    }

    @Test
    fun whenArmedAndReleasedSlowlyThenStartsNewChat() {
        assertTrue(startsNewChat(isArmed = true, velocityPxPerSec = 200f, maxReleaseVelocityPx = 3000f))
    }

    @Test
    fun whenArmedButFlickedThenDoesNotStartNewChat() {
        assertFalse(startsNewChat(isArmed = true, velocityPxPerSec = 8000f, maxReleaseVelocityPx = 3000f))
    }

    @Test
    fun whenArmedAndMovingBackDownAtReleaseThenStartsNewChat() {
        assertTrue(startsNewChat(isArmed = true, velocityPxPerSec = -500f, maxReleaseVelocityPx = 3000f))
    }

    @Test
    fun whenNotArmedThenDoesNotStartNewChat() {
        assertFalse(startsNewChat(isArmed = false, velocityPxPerSec = 0f, maxReleaseVelocityPx = 3000f))
    }
}
