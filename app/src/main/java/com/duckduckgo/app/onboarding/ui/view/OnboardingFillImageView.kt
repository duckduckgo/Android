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

package com.duckduckgo.app.onboarding.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.updateLayoutParams

/**
 * ImageView that, in "fill" mode, scales its drawable to cover a fixed height and crops the horizontal
 * overflow anchored to the END (right) + BOTTOM edges (see [endBottomFillTransform]). With no fill height
 * it behaves as a plain ImageView, honouring its XML scaleType / maxHeight / adjustViewBounds.
 */
open class OnboardingFillImageView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private data class Defaults(
        val scaleType: ScaleType,
        val adjustViewBounds: Boolean,
        val maxHeight: Int,
        val layoutHeight: Int,
    )

    private var fillHeightPx: Int = 0
    private var defaults: Defaults? = null

    fun setFillHeight(heightPx: Int, maxHeightFraction: Float = 1f) {
        if (defaults == null) {
            defaults = Defaults(
                scaleType = scaleType,
                adjustViewBounds = adjustViewBounds,
                maxHeight = maxHeight,
                layoutHeight = layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        fillHeightPx = cappedFillHeightPx(heightPx, resources.displayMetrics.heightPixels, maxHeightFraction)
        adjustViewBounds = false
        maxHeight = Int.MAX_VALUE
        scaleType = ScaleType.MATRIX
        updateLayoutParams { height = fillHeightPx }
        applyFillMatrix()
    }

    fun clearFill() {
        val original = defaults ?: return
        fillHeightPx = 0
        imageMatrix = Matrix()
        adjustViewBounds = original.adjustViewBounds
        scaleType = original.scaleType
        maxHeight = original.maxHeight
        updateLayoutParams { height = original.layoutHeight }
    }

    override fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        val current = scaleType
        super.setAdjustViewBounds(adjustViewBounds)
        if (scaleType != current) scaleType = current
    }

    override fun onDraw(canvas: Canvas) {
        // Parent new-tab layout uses clipChildren="false" (for the Dax/fin), so a MATRIX drawable would
        // overflow the view; clip to bounds in fill mode so the fill height controls the rendered size.
        if (fillHeightPx > 0) canvas.clipRect(0, 0, width, height)
        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fillHeightPx > 0) applyFillMatrix()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        if (fillHeightPx > 0) applyFillMatrix()
    }

    private fun applyFillMatrix() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val t = endBottomFillTransform(width, height, d.intrinsicWidth, d.intrinsicHeight)
        imageMatrix = Matrix().apply {
            setScale(t.scale, t.scale)
            postTranslate(t.translateX, t.translateY)
        }
    }
}
