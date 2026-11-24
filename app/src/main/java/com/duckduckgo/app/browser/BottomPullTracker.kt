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

import kotlin.math.abs

/**
 * Measures how far a finger drags up once the page can't scroll any further down.
 *
 * Positions are on screen, not in the WebView: the pull slides the WebView, which would otherwise move the
 * finger's position under it and feed back into the pull. The WebView adds [touchOffset] to every touch it
 * hands the page, so the page never sees the pull, and dragging back down hands any movement past zero
 * straight back to the page.
 *
 * A finger held still for [longPressTimeoutMs] before moving is a long press (text selection, context
 * menu) rather than a swipe, so it never pulls.
 *
 * Only one finger is followed at a time; others are ignored until it lifts (see [onPointerChanged]).
 */
internal class BottomPullTracker(
    private val touchSlopPx: Int,
    private val longPressTimeoutMs: Long,
) {

    var distance: Int = 0
        private set

    val isPulling: Boolean get() = distance > 0 && !isFrozen

    private var isAtBottom = false
    private var isFrozen = false
    private var isHeld = false
    private var hasMoved = false
    private var downY = 0
    private var downTimeMs = 0L
    private var lastY = 0
    private var lastDelta = 0
    private var pullStartViewTop = 0
    private val recentMoves = ArrayDeque<Sample>()

    fun onDown(y: Int, timeMs: Long) {
        distance = 0
        isAtBottom = false
        isFrozen = false
        isHeld = false
        hasMoved = false
        downY = y
        downTimeMs = timeMs
        lastY = y
        lastDelta = 0
        recentMoves.clear()
        record(y, timeMs)
    }

    /** @return whether [distance] changed. */
    fun onMove(y: Int, timeMs: Long, viewTop: Int): Boolean {
        record(y, timeMs)
        if (!hasMoved && abs(y - downY) > touchSlopPx) {
            hasMoved = true
            isHeld = timeMs - downTimeMs >= longPressTimeoutMs
        }
        val delta = lastY - y
        lastY = y
        lastDelta = delta
        if (!isAtBottom || isFrozen) return false
        val newDistance = (distance + delta).coerceAtLeast(0)
        if (newDistance == 0 && delta < 0) isAtBottom = false
        if (distance == 0) pullStartViewTop = viewTop
        val changed = newDistance != distance
        distance = newDistance
        return changed
    }

    /**
     * Another finger takes over from the one being followed, which has lifted. The pull carries on from where it
     * is, so the gap between the two fingers is neither a pull, nor a swipe, nor release speed.
     */
    fun onPointerChanged(y: Int, timeMs: Long) {
        if (!hasMoved) downY = y
        lastY = y
        recentMoves.clear()
        record(y, timeMs)
    }

    fun onUp(y: Int, timeMs: Long) {
        record(y, timeMs)
    }

    /** How fast the finger was moving up as it let go, in pixels per second; negative when moving down. */
    fun releaseVelocity(): Float {
        val first = recentMoves.firstOrNull() ?: return 0f
        val last = recentMoves.last()
        val elapsedMs = last.timeMs - first.timeMs
        if (elapsedMs <= 0) return 0f
        return (first.y - last.y) * MS_PER_SECOND / elapsedMs
    }

    private fun record(y: Int, timeMs: Long) {
        recentMoves.addLast(Sample(y, timeMs))
        while (timeMs - recentMoves.first().timeMs > VELOCITY_WINDOW_MS) recentMoves.removeFirst()
    }

    /** What to add to a touch's position in the WebView so the page sees the finger stay where the pull began. */
    fun touchOffset(viewTop: Int): Int = if (distance > 0) distance + viewTop - pullStartViewTop else 0

    /**
     * The page reports overscroll on its own schedule, after the move that caused it, so the latest move's
     * direction tells a bottom overscroll from a top one. Once a pull has started the page sees no movement.
     */
    fun onOverScrolled(clampedY: Boolean, canScrollDown: Boolean, canStartPull: () -> Boolean) {
        if (distance > 0 || isFrozen) return
        isAtBottom = clampedY && lastDelta > 0 && !canScrollDown && !isHeld && canStartPull()
    }

    /** Ends the gesture's pull but keeps [distance], so the page doesn't see the finger jump. */
    fun freeze() {
        isFrozen = true
        isAtBottom = false
    }

    private data class Sample(val y: Int, val timeMs: Long)

    private companion object {
        const val VELOCITY_WINDOW_MS = 100L
        const val MS_PER_SECOND = 1000f
    }
}

interface BottomPullListener {
    fun canStartPull(): Boolean

    fun onPull(distancePx: Int)

    /** @param velocityPxPerSec how fast the finger was moving up as it let go; negative when moving down. */
    fun onPullReleased(velocityPxPerSec: Float)

    fun onPullCancelled()
}
