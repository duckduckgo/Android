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

package com.duckduckgo.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomPullTrackerTest {

    private val tracker = BottomPullTracker(touchSlopPx = 10, longPressTimeoutMs = 500)

    @Test
    fun whenNotAtBottomThenMovingUpDoesNotPull() {
        tracker.onDown(1000, timeMs = 0)

        assertFalse(tracker.onMove(900, timeMs = 100, viewTop = 100))
        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenOverscrolledAtBottomThenMovingUpPulls() {
        reachBottom()

        assertTrue(tracker.onMove(850, timeMs = 100, viewTop = 100))
        assertTrue(tracker.onMove(800, timeMs = 100, viewTop = 100))

        assertEquals(100, tracker.distance)
        assertTrue(tracker.isPulling)
    }

    @Test
    fun whenOverscrolledAtTopThenMovingDownDoesNotPull() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(1100, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = true) { true }

        tracker.onMove(1200, timeMs = 100, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenPageCanStillScrollDownThenOverscrollDoesNotStartPull() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = true) { true }

        tracker.onMove(800, timeMs = 100, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenPullNotAllowedThenOverscrollDoesNotStartPull() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { false }

        tracker.onMove(800, timeMs = 100, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenOverscrollNotClampedThenPullDoesNotStart() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = false, canScrollDown = false) { true }

        tracker.onMove(800, timeMs = 100, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenDraggingBackDownThenPullShrinks() {
        reachBottom()
        tracker.onMove(800, timeMs = 100, viewTop = 100)

        tracker.onMove(850, timeMs = 100, viewTop = 100)

        assertEquals(50, tracker.distance)
    }

    @Test
    fun whenDraggingBackDownPastStartThenPullEndsAndLaterMovesGoToThePage() {
        reachBottom()
        tracker.onMove(800, timeMs = 100, viewTop = 100)

        tracker.onMove(950, timeMs = 100, viewTop = 100)
        assertEquals(0, tracker.distance)

        assertFalse(tracker.onMove(800, timeMs = 100, viewTop = 100))
        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenOverscrollReportedDuringPullThenPullContinues() {
        reachBottom()
        tracker.onMove(800, timeMs = 100, viewTop = 100)

        tracker.onOverScrolled(clampedY = false, canScrollDown = true) { true }
        tracker.onMove(750, timeMs = 100, viewTop = 100)

        assertEquals(150, tracker.distance)
    }

    @Test
    fun whenFrozenThenDistanceIsKeptButNoLongerChanges() {
        reachBottom()
        tracker.onMove(800, timeMs = 100, viewTop = 100)

        tracker.freeze()

        assertFalse(tracker.onMove(700, timeMs = 100, viewTop = 100))
        assertEquals(100, tracker.distance)
        assertFalse(tracker.isPulling)
    }

    @Test
    fun whenFrozenBeforePullStartsThenGestureNeverPulls() {
        tracker.onDown(1000, timeMs = 0)
        tracker.freeze()
        tracker.onMove(900, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { true }

        assertFalse(tracker.onMove(800, timeMs = 200, viewTop = 100))
        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenPullingFingerLiftsThenPullContinuesFromTheOtherFinger() {
        reachBottom()
        tracker.onMove(800, timeMs = 150, viewTop = 100)

        tracker.onPointerChanged(500, timeMs = 160)
        assertEquals(100, tracker.distance)

        assertTrue(tracker.onMove(450, timeMs = 170, viewTop = 100))
        assertEquals(150, tracker.distance)
    }

    @Test
    fun whenPullingFingerChangesThenTheGapBetweenFingersIsNotReleaseVelocity() {
        reachBottom()
        tracker.onMove(800, timeMs = 150, viewTop = 100)

        tracker.onPointerChanged(300, timeMs = 160)
        tracker.onUp(300, timeMs = 200)

        assertEquals(0f, tracker.releaseVelocity(), 1f)
    }

    @Test
    fun whenFingerChangesWhileHeldThenTheGapBetweenFingersIsNotASwipe() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onPointerChanged(700, timeMs = 100)
        tracker.onMove(698, timeMs = 200, viewTop = 100)
        tracker.onMove(600, timeMs = 600, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { true }

        tracker.onMove(500, timeMs = 700, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenNewGestureStartsThenPullResets() {
        reachBottom()
        tracker.onMove(800, timeMs = 100, viewTop = 100)

        tracker.onDown(500, timeMs = 0)

        assertEquals(0, tracker.distance)
        assertFalse(tracker.onMove(400, timeMs = 100, viewTop = 100))
    }

    @Test
    fun whenFingerHeldBeforeMovingThenOverscrollDoesNotStartPull() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 600, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { true }

        tracker.onMove(800, timeMs = 700, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenFingerOnlyJittersWithinTouchSlopBeforeLongPressThenItStillCountsAsHeld() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(995, timeMs = 100, viewTop = 100)
        tracker.onMove(900, timeMs = 600, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { true }

        tracker.onMove(800, timeMs = 700, viewTop = 100)

        assertEquals(0, tracker.distance)
    }

    @Test
    fun whenChatSlidesUpWithThePullThenPullStillFollowsTheFinger() {
        reachBottom()

        tracker.onMove(850, timeMs = 100, viewTop = 100)
        tracker.onMove(800, timeMs = 100, viewTop = 50)
        tracker.onMove(750, timeMs = 100, viewTop = 0)

        assertEquals(150, tracker.distance)
    }

    @Test
    fun whenChatSlidesUpWithThePullThenThePageSeesTheFingerStayPut() {
        reachBottom()
        val seenBeforePull = 900 - 100

        tracker.onMove(850, timeMs = 100, viewTop = 100)
        assertEquals(seenBeforePull, 850 - 100 + tracker.touchOffset(viewTop = 100))

        tracker.onMove(800, timeMs = 100, viewTop = 50)
        assertEquals(seenBeforePull, 800 - 50 + tracker.touchOffset(viewTop = 50))
    }

    @Test
    fun whenNotPullingThenTouchesReachThePageUnchanged() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 100, viewTop = 100)

        assertEquals(0, tracker.touchOffset(viewTop = 100))
    }

    @Test
    fun whenFingerFlicksUpThenReleaseVelocityIsHigh() {
        reachBottom()
        tracker.onMove(850, timeMs = 110, viewTop = 100)
        tracker.onMove(700, timeMs = 120, viewTop = 100)
        tracker.onMove(550, timeMs = 130, viewTop = 100)

        tracker.onUp(450, timeMs = 140)

        assertEquals(11_250f, tracker.releaseVelocity(), 1f)
    }

    @Test
    fun whenFingerStopsBeforeReleaseThenReleaseVelocityIsLow() {
        reachBottom()
        tracker.onMove(700, timeMs = 200, viewTop = 100)
        tracker.onMove(700, timeMs = 300, viewTop = 100)
        tracker.onMove(699, timeMs = 380, viewTop = 100)

        tracker.onUp(699, timeMs = 400)

        assertEquals(10f, tracker.releaseVelocity(), 1f)
    }

    @Test
    fun whenFingerMovesDownAtReleaseThenReleaseVelocityIsNegative() {
        reachBottom()
        tracker.onMove(600, timeMs = 200, viewTop = 100)

        tracker.onMove(650, timeMs = 250, viewTop = 100)
        tracker.onUp(700, timeMs = 300)

        assertTrue(tracker.releaseVelocity() < 0)
    }

    private fun reachBottom() {
        tracker.onDown(1000, timeMs = 0)
        tracker.onMove(900, timeMs = 100, viewTop = 100)
        tracker.onOverScrolled(clampedY = true, canScrollDown = false) { true }
    }
}
