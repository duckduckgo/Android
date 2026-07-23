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

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Draws the indeterminate animation: a fast accent-colored sweep followed by a slow sweep on a
 * continuous [ProgressBarConfig.indeterminateCycleMs] cycle. The first fast sweep is seeded from the
 * current fill (see [start]) for a seamless hand-off. Call [requestFinish] when leaving the
 * indeterminate state to let the active fast-slow sequence finish and empty the bar before the
 * determinate fill resumes; [hasFinished] reports when it has.
 */
class IndeterminateSweepRenderer(
    private val config: ProgressBarConfig,
    barColor: Int,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor
        style = Paint.Style.FILL
    }

    private var startTime = 0L
    private var seedLeading = 0f
    private var endTime = 0L
    private var finalCycleIndex: Long? = null

    val isActive: Boolean get() = startTime > 0L

    /** @param seedLeadingFraction the current fill as a track fraction (0..1) at hand-off. */
    fun start(now: Long, seedLeadingFraction: Float) {
        startTime = now
        seedLeading = seedLeadingFraction.coerceIn(0f, 1f)
        endTime = 0L
        finalCycleIndex = null
    }

    fun stop() {
        startTime = 0L
        endTime = 0L
        finalCycleIndex = null
    }

    /**
     * Stop looping: finish the current fast-slow sequence, then report done via [hasFinished].
     * Sweeps from the next sequence are suppressed while the final slow sweep exits.
     */
    fun requestFinish(now: Long) {
        if (!isActive || endTime > 0L) return
        val finishTarget = IndeterminateSweepGeometry.calculateFinishTarget(startTime, now, config.indeterminateCycleMs)
        finalCycleIndex = finishTarget.cycleIndex
        endTime = finishTarget.endTime
    }

    fun hasFinished(now: Long): Boolean = endTime in 1..now

    fun draw(canvas: Canvas, trackWidth: Float, top: Float, bottom: Float, now: Long) {
        if (!isActive || trackWidth <= 0f) return
        val elapsed = now - startTime
        val radius = (bottom - top) / 2f
        val lastCycle = finalCycleIndex
        for (segment in IndeterminateSweepGeometry.calculateSegments(elapsed, config.indeterminateCycleMs, seedLeading)) {
            if (lastCycle != null && segment.cycleIndex > lastCycle) continue
            val left = segment.edges.trailing * trackWidth
            val right = segment.edges.leading * trackWidth
            if (right - left > 0f) {
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
            }
        }
    }
}
