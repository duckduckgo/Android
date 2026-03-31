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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.pow
import kotlin.math.roundToInt

class ShimmerRenderer(
    private val config: ProgressBarConfig,
    private val density: Float,
) {

    private val bandStartWidthPx = config.shimmerBandStartWidthDp * density
    private val bandEndWidthPx = config.shimmerBandEndWidthDp * density

    private val bandMatrix = Matrix()
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shimmerStartTime = 0L
    private var cachedGradients: Array<LinearGradient>? = null

    val bandCount: Int = 3
    val isActive: Boolean get() = shimmerStartTime > 0L

    fun start(now: Long) {
        shimmerStartTime = now
    }

    fun stop() {
        shimmerStartTime = 0L
    }

    fun draw(
        canvas: Canvas,
        progressWidth: Float,
        top: Float,
        bottom: Float,
        now: Long,
    ) {
        if (!isActive || progressWidth <= 0f) return

        canvas.save()
        canvas.clipRect(0f, top, progressWidth, bottom)

        val elapsed = now - shimmerStartTime

        for (i in 0 until bandCount) {
            val bandElapsed = elapsed - (i * config.shimmerBandDelay)
            if (bandElapsed < 0) continue

            val cycleProgress = (bandElapsed % config.shimmerSpeed).toFloat() / config.shimmerSpeed
            val eased = easeOutQuint(cycleProgress)

            val totalTravel = progressWidth + bandStartWidthPx
            val bandLeft = -bandStartWidthPx + eased * totalTravel
            val bandWidth = bandStartWidthPx +
                eased * (bandEndWidthPx - bandStartWidthPx)
            val alpha = config.shimmerBandStartOpacity +
                eased * (config.shimmerBandEndOpacity - config.shimmerBandStartOpacity)

            ensureGradientCached()
            bandMatrix.setTranslate(bandLeft, 0f)
            bandMatrix.preScale(bandWidth / bandStartWidthPx, 1f)

            val gradient = cachedGradients!![i]
            gradient.setLocalMatrix(bandMatrix)
            bandPaint.shader = gradient
            bandPaint.alpha = (alpha * 255).roundToInt()

            canvas.drawRect(bandLeft, top, bandLeft + bandWidth, bottom, bandPaint)
        }

        canvas.restore()
    }

    private fun ensureGradientCached() {
        if (cachedGradients != null) return
        cachedGradients = Array(bandCount) {
            LinearGradient(
                0f, 0f, bandStartWidthPx, 0f,
                intArrayOf(
                    Color.argb(0, 255, 255, 255),
                    Color.argb(204, 255, 255, 255), // 80%
                    Color.argb(255, 255, 255, 255), // 100%
                    Color.argb(204, 255, 255, 255), // 80%
                    Color.argb(0, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    companion object {
        fun easeOutQuint(t: Float): Float = 1f - (1f - t).pow(5)
    }
}
