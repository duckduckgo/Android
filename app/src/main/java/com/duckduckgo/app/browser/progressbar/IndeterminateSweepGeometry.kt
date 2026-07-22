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

package com.duckduckgo.app.browser.progressbar

import kotlin.math.pow

/** Segment edges as track fractions (0..1). The visible segment is [trailing, leading]. */
data class SweepEdges(val trailing: Float, val leading: Float)

/**
 * Pure geometry for the indeterminate sweep: a segment that grows from the left, travels, and
 * contracts off the right on a repeating cycle. The first cycle can be seeded so its leading edge
 * starts at the current determinate fill, for a seamless hand-off.
 */
object IndeterminateSweepGeometry {

    // Trailing edge stays pinned for the first part of the cycle, so the segment grows before it travels.
    private const val TRAILING_DELAY = 0.4f

    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

    /**
     * @param cycleProgress 0..1 within the current cycle.
     * @param seedLeading leading-edge start value; the current fill P for the first cycle, 0 otherwise.
     */
    fun calculateEdges(cycleProgress: Float, seedLeading: Float): SweepEdges {
        val s = cycleProgress.coerceIn(0f, 1f)
        val seed = seedLeading.coerceIn(0f, 1f)
        val leading = seed + (1f - seed) * easeInOutCubic(s)
        val trailing = if (s <= TRAILING_DELAY) {
            0f
        } else {
            easeInOutCubic((s - TRAILING_DELAY) / (1f - TRAILING_DELAY))
        }
        return SweepEdges(trailing = trailing.coerceIn(0f, leading), leading = leading)
    }

    /** The seed applies only to the first cycle; every later cycle starts empty. */
    fun seedForCycle(elapsedMs: Long, cycleMs: Long, seedLeading: Float): Float =
        if (cycleMs > 0L && elapsedMs / cycleMs == 0L) seedLeading else 0f

    /**
     * The next cycle boundary at or after [now] — where the segment has fully exited and the bar is
     * empty. Used to finish the current cycle gracefully when leaving the indeterminate state.
     */
    fun cycleEnd(startTime: Long, now: Long, cycleMs: Long): Long {
        if (cycleMs <= 0L) return now
        val elapsedCycles = (now - startTime).coerceAtLeast(0L) / cycleMs
        return startTime + (elapsedCycles + 1) * cycleMs
    }
}
