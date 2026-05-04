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

package com.duckduckgo.app.browser.pdf

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * Speech-bubble drawable with a wave tail at the top or bottom edge (right-aligned).
 * Use [WavePosition.TOP] for popups below the anchor, [WavePosition.BOTTOM] for popups above it.
 */
class BubbleTooltipDrawable(
    @ColorInt private val backgroundColor: Int,
    private val wavePosition: WavePosition = WavePosition.TOP,
    private val density: Float = DEFAULT_DENSITY,
) : Drawable() {

    enum class WavePosition { TOP, BOTTOM }

    private val cornerRadiusPx by lazy { dp(12f) }
    private val cornerRadiusTrPx by lazy { dp(10f) }
    private val waveWidthPx by lazy { dp(24f) }
    private val waveHeightPx by lazy { dp(12f) }
    private val waveOffsetEndPx by lazy { dp(8f) }
    private val horizPaddingPx by lazy { dp(20f).toInt() }
    private val vertPaddingPx by lazy { dp(12f).toInt() }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }

    private val bodyPath = Path()
    private val bodyRect = RectF()

    override fun getPadding(padding: Rect): Boolean {
        val waveSide = (waveHeightPx + vertPaddingPx).toInt()
        when (wavePosition) {
            WavePosition.TOP -> padding.set(horizPaddingPx, waveSide, horizPaddingPx, vertPaddingPx)
            WavePosition.BOTTOM -> padding.set(horizPaddingPx, vertPaddingPx, horizPaddingPx, waveSide)
        }
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath(bounds)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.drawPath(bodyPath, bodyPaint)
    }

    // Outline excludes the wave so the elevation shadow follows only the body. setRoundRect
    // takes a single radius, and Outline.setPath with non-convex paths is API 30+; this is the
    // minSdk 26 fallback.
    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        val waveInset = waveHeightPx.toInt()
        when (wavePosition) {
            WavePosition.TOP -> outline.setRoundRect(b.left, b.top + waveInset, b.right, b.bottom, cornerRadiusPx)
            WavePosition.BOTTOM -> outline.setRoundRect(b.left, b.top, b.right, b.bottom - waveInset, cornerRadiusPx)
        }
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1
    override fun getIntrinsicHeight(): Int = -1

    private fun rebuildPath(b: Rect) {
        bodyPath.reset()

        val left = b.left.toFloat()
        val right = b.right.toFloat()
        val (bodyTop, bodyBottom) = when (wavePosition) {
            WavePosition.TOP -> b.top.toFloat() + waveHeightPx to b.bottom.toFloat()
            WavePosition.BOTTOM -> b.top.toFloat() to b.bottom.toFloat() - waveHeightPx
        }

        bodyRect.set(left, bodyTop, right, bodyBottom)

        // Corners order: TL, TR, BR, BL. The corner where the wave attaches uses the smaller radius.
        val radii = when (wavePosition) {
            WavePosition.TOP -> floatArrayOf(
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusTrPx,
                cornerRadiusTrPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
            )
            WavePosition.BOTTOM -> floatArrayOf(
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusTrPx,
                cornerRadiusTrPx,
                cornerRadiusPx,
                cornerRadiusPx,
            )
        }
        bodyPath.addRoundRect(bodyRect, radii, Path.Direction.CW)

        val baseRight = right - waveOffsetEndPx
        val baseLeft = baseRight - waveWidthPx
        val baselineY = if (wavePosition == WavePosition.TOP) bodyTop else bodyBottom
        val peakY = if (wavePosition == WavePosition.TOP) b.top.toFloat() else b.bottom.toFloat()

        val wavePath = Path()
        wavePath.moveTo(baseLeft, baselineY)
        wavePath.cubicTo(
            baseLeft + dp(8f),
            baselineY,
            baseLeft + dp(10f),
            peakY,
            baseLeft + dp(14f),
            peakY,
        )
        wavePath.cubicTo(
            baseLeft + dp(17f),
            peakY,
            baseLeft + dp(19f),
            baselineY,
            baseRight,
            baselineY,
        )
        wavePath.close()

        bodyPath.op(wavePath, Path.Op.UNION)
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        val DEFAULT_DENSITY: Float
            get() = Resources.getSystem().displayMetrics.density
    }
}
