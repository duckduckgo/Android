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

package com.duckduckgo.app.icon.ui

import android.R.attr
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * Strokes the launcher's own icon mask so the selection outline matches whatever shape the user's
 * launcher applies to adaptive icons. A fixed oval only lines up on devices masking to a circle.
 */
class AppIconOutlineDrawable(
    private val strokeWidthPx: Float,
    private val outlineColor: Int,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = outlineColor
    }

    private val maskSource = AdaptiveIconDrawable(null, null)
    private val outline = Path()
    private var selected = false

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val isSelected = state.contains(attr.state_selected)
        if (isSelected == selected) return false
        selected = isSelected
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        val inset = (strokeWidthPx / 2f).roundToInt()
        maskSource.setBounds(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset,
        )
        // getIconMask() hands back the drawable's own Path, which it rewrites on every bounds change.
        outline.set(maskSource.iconMask)
    }

    override fun draw(canvas: Canvas) {
        if (!selected) return
        canvas.drawPath(outline, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable, still abstract so it has to be implemented")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
