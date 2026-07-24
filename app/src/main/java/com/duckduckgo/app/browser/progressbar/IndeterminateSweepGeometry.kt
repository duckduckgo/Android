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

import kotlin.math.roundToLong

/** Segment edges as track fractions (0..1). The visible segment is [trailing, leading]. */
data class SweepEdges(val trailing: Float, val leading: Float)

enum class SweepPass {
    FAST,
    SLOW,
}

data class SweepSegment(
    val cycleIndex: Long,
    val pass: SweepPass,
    val edges: SweepEdges,
)

data class SweepFinishTarget(
    val cycleIndex: Long,
    val endTime: Long,
)

/**
 * Pure geometry for the indeterminate animation: a fast sweep followed by a slow sweep on each
 * repeating cycle. Each sweep grows from the left, travels, and contracts off the right. The first
 * fast sweep can be seeded so its leading edge starts at the current determinate fill, for a
 * seamless hand-off.
 */
object IndeterminateSweepGeometry {

    private const val FAST_TRAILING_DELAY = 0.34f
    private const val SLOW_TRAILING_DELAY = 0.48f

    private const val EASING_X1 = 0.25f
    private const val EASING_Y1 = 0.1f
    private const val EASING_X2 = 0.25f
    private const val EASING_Y2 = 1f
    private const val EASING_ITERATIONS = 12

    private const val FAST_SWEEP_DURATION_FRACTION = 0.35f
    private const val SLOW_SWEEP_START_FRACTION = 0.45f
    private const val SLOW_SWEEP_DURATION_FRACTION = 0.575f

    /**
     * @param sweepProgress 0..1 within the current sweep.
     * @param seedLeading leading-edge start value; the current fill P for the first cycle, 0 otherwise.
     */
    fun calculateEdges(
        sweepProgress: Float,
        seedLeading: Float,
        pass: SweepPass,
    ): SweepEdges {
        val s = sweepProgress.coerceIn(0f, 1f)
        val seed = seedLeading.coerceIn(0f, 1f)
        val leading = seed + (1f - seed) * calculateEase(s)
        val trailingDelay = when (pass) {
            SweepPass.FAST -> FAST_TRAILING_DELAY
            SweepPass.SLOW -> SLOW_TRAILING_DELAY
        }
        val trailing = if (s <= trailingDelay) {
            0f
        } else {
            (s - trailingDelay) / (1f - trailingDelay)
        }
        return SweepEdges(trailing = trailing.coerceIn(0f, leading), leading = leading)
    }

    /**
     * Returns every sweep active at [elapsedMs]. Usually this is one segment, but the end of a slow
     * sweep briefly overlaps the next fast sweep at the cycle boundary.
     */
    fun calculateSegments(
        elapsedMs: Long,
        cycleMs: Long,
        seedLeading: Float,
    ): List<SweepSegment> {
        if (cycleMs <= 0L) return emptyList()

        val elapsed = elapsedMs.coerceAtLeast(0L)
        val cycleIndex = elapsed / cycleMs
        val cycleElapsed = elapsed % cycleMs
        val fastDuration = getFastSweepDuration(cycleMs)
        val slowStart = getSlowSweepStart(cycleMs)
        val slowDuration = getSlowSweepDuration(cycleMs)

        return buildList {
            if (cycleIndex > 0L) {
                addSlowSweepIfActive(
                    cycleIndex = cycleIndex - 1L,
                    sweepElapsed = cycleElapsed + cycleMs - slowStart,
                    duration = slowDuration,
                )
            }

            if (cycleElapsed <= fastDuration) {
                val seed = if (cycleIndex == 0L) seedLeading else 0f
                add(
                    SweepSegment(
                        cycleIndex = cycleIndex,
                        pass = SweepPass.FAST,
                        edges = calculateEdges(cycleElapsed.toFloat() / fastDuration, seed, SweepPass.FAST),
                    ),
                )
            }

            addSlowSweepIfActive(
                cycleIndex = cycleIndex,
                sweepElapsed = cycleElapsed - slowStart,
                duration = slowDuration,
            )
        }
    }

    /**
     * Selects the sequence to finish and the time when its slow sweep has fully exited. During the
     * brief overlap at a nominal cycle boundary, the previous sequence remains the finish target
     * and the newly-started fast sweep is suppressed.
     */
    fun calculateFinishTarget(startTime: Long, now: Long, cycleMs: Long): SweepFinishTarget {
        if (cycleMs <= 0L) return SweepFinishTarget(cycleIndex = 0L, endTime = now)

        val elapsed = (now - startTime).coerceAtLeast(0L)
        val nominalCycleIndex = elapsed / cycleMs
        val cycleElapsed = elapsed % cycleMs
        val sequenceEndOffset = getSlowSweepStart(cycleMs) + getSlowSweepDuration(cycleMs)
        val overlapDuration = (sequenceEndOffset - cycleMs).coerceAtLeast(0L)
        val finishingCycleIndex = if (nominalCycleIndex > 0L && cycleElapsed < overlapDuration) {
            nominalCycleIndex - 1L
        } else {
            nominalCycleIndex
        }

        return SweepFinishTarget(
            cycleIndex = finishingCycleIndex,
            endTime = startTime + finishingCycleIndex * cycleMs + sequenceEndOffset,
        )
    }

    private fun MutableList<SweepSegment>.addSlowSweepIfActive(
        cycleIndex: Long,
        sweepElapsed: Long,
        duration: Long,
    ) {
        if (sweepElapsed !in 0..duration) return
        add(
            SweepSegment(
                cycleIndex = cycleIndex,
                pass = SweepPass.SLOW,
                edges = calculateEdges(sweepElapsed.toFloat() / duration, seedLeading = 0f, SweepPass.SLOW),
            ),
        )
    }

    private fun calculateEase(t: Float): Float {
        val targetX = t.coerceIn(0f, 1f)
        if (targetX == 0f || targetX == 1f) return targetX
        var lower = 0f
        var upper = 1f
        repeat(EASING_ITERATIONS) {
            val candidate = (lower + upper) / 2f
            if (calculateCubicBezierCoordinate(candidate, EASING_X1, EASING_X2) < targetX) {
                lower = candidate
            } else {
                upper = candidate
            }
        }
        return calculateCubicBezierCoordinate((lower + upper) / 2f, EASING_Y1, EASING_Y2)
    }

    private fun calculateCubicBezierCoordinate(
        t: Float,
        controlPoint1: Float,
        controlPoint2: Float,
    ): Float {
        val oneMinusT = 1f - t
        return 3f * oneMinusT * oneMinusT * t * controlPoint1 +
            3f * oneMinusT * t * t * controlPoint2 +
            t * t * t
    }

    private fun getFastSweepDuration(cycleMs: Long): Long =
        (cycleMs * FAST_SWEEP_DURATION_FRACTION).roundToLong().coerceAtLeast(1L)

    private fun getSlowSweepStart(cycleMs: Long): Long =
        (cycleMs * SLOW_SWEEP_START_FRACTION).roundToLong()

    private fun getSlowSweepDuration(cycleMs: Long): Long =
        (cycleMs * SLOW_SWEEP_DURATION_FRACTION).roundToLong().coerceAtLeast(1L)
}
