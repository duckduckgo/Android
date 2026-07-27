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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

class CameraScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val scrimPath = Path().apply {
        fillType = Path.FillType.EVEN_ODD
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCRIM_COLOR
    }
    private val cutoutRect = RectF()

    private val cornerRect = RectF()
    private val cornerRadius = CUTOUT_CORNER_RADIUS_DP.toPx()

    private val armPath = Path()
    private val cornerPath = Path()
    private val mirrorMatrix = Matrix()
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ARM_STROKE_WIDTH_DP.toPx()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, CommonR.color.blue20)
    }

    @FloatRange(from = 0.0, to = 1.0)
    var cutoutSizeFraction: Float = 0.5f
        set(value) {
            if (field == value) return
            field = value
            rebuildGeometry()
            invalidate()
        }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(scrimPath, scrimPaint)
        canvas.drawPath(armPath, armPaint)
    }

    private fun rebuildGeometry() {
        if (width == 0 || height == 0) return

        val side = minOf(width, height) * cutoutSizeFraction
        val top = (height - side) * CUTOUT_TOP_SPACE_FRACTION
        cutoutRect.set((width - side) / 2, top, (width + side) / 2, top + side)

        scrimPath.reset()
        scrimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        scrimPath.addRoundRect(cutoutRect, cornerRadius, cornerRadius, Path.Direction.CW)

        // the other three corners are mirror images of the top-left one around the cutout center
        buildTopLeftCutoutCorner()
        armPath.rewind()
        for (scaleX in MIRROR_SCALES) {
            for (scaleY in MIRROR_SCALES) {
                mirrorMatrix.setScale(scaleX, scaleY, cutoutRect.centerX(), cutoutRect.centerY())
                armPath.addPath(cornerPath, mirrorMatrix)
            }
        }
    }

    private fun buildTopLeftCutoutCorner() {
        val r = cornerRadius
        val arm = cutoutRect.width() * ARM_LENGTH_FRACTION
        cornerPath.rewind()
        with(cutoutRect) {
            cornerRect.set(left, top, left + 2 * r, top + 2 * r)
            cornerPath.moveTo(left, top + r + arm)
            cornerPath.arcTo(cornerRect, 180f, 90f)
            cornerPath.lineTo(left + r + arm, top)
        }
    }

    companion object {
        const val CUTOUT_TOP_SPACE_FRACTION = 0.55f

        private val MIRROR_SCALES = listOf(1f, -1f)
        private const val SCRIM_COLOR = 0x80000000.toInt()
        private const val CUTOUT_CORNER_RADIUS_DP = 28f
        private const val ARM_STROKE_WIDTH_DP = 4f
        private const val ARM_LENGTH_FRACTION = 0.2f
    }
}
