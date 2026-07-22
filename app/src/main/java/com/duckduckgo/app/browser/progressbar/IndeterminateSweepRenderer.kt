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
 * Draws the indeterminate sweep: a single accent-coloured segment that grows from the left, travels,
 * and contracts off the right on a continuous [ProgressBarConfig.indeterminateCycleMs] cycle. The
 * first cycle is seeded from the current fill (see [start]) for a seamless hand-off. Call
 * [requestFinish] when leaving the indeterminate state to let the current cycle finish and empty the
 * bar before the determinate fill resumes; [hasFinished] reports when it has.
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

    val isActive: Boolean get() = startTime > 0L

    /** @param seedLeadingFraction the current fill as a track fraction (0..1) at hand-off. */
    fun start(now: Long, seedLeadingFraction: Float) {
        startTime = now
        seedLeading = seedLeadingFraction.coerceIn(0f, 1f)
        endTime = 0L
    }

    fun stop() {
        startTime = 0L
        endTime = 0L
    }

    /**
     * Stop looping: finish the current cycle, then report done via [hasFinished]. The segment
     * contracts off the right at the cycle boundary, leaving the bar empty for the determinate hand-back.
     */
    fun requestFinish(now: Long) {
        if (!isActive || endTime > 0L) return
        endTime = IndeterminateSweepGeometry.cycleEnd(startTime, now, config.indeterminateCycleMs)
    }

    fun hasFinished(now: Long): Boolean = endTime in 1..now

    fun draw(canvas: Canvas, trackWidth: Float, top: Float, bottom: Float, now: Long) {
        if (!isActive || trackWidth <= 0f) return
        val elapsed = now - startTime
        val s = (elapsed % config.indeterminateCycleMs).toFloat() / config.indeterminateCycleMs
        val seed = IndeterminateSweepGeometry.seedForCycle(elapsed, config.indeterminateCycleMs, seedLeading)
        val edges = IndeterminateSweepGeometry.calculateEdges(s, seed)
        val left = edges.trailing * trackWidth
        val right = edges.leading * trackWidth
        if (right - left <= 0f) return
        val radius = (bottom - top) / 2f
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }
}
