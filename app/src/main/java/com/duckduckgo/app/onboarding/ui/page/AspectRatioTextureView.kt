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

package com.duckduckgo.app.onboarding.ui.page

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.res.use
import com.duckduckgo.app.browser.R

class AspectRatioTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs) {

    private var videoWidth = 0
    private var videoHeight = 0
    private var bottomCornerRadiusPx = 0f
    private var maxWidthPx = NO_MAX_WIDTH

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Push the rect's top above the view so only the bottom corners end up rounded.
                outline.setRoundRect(0, -bottomCornerRadiusPx.toInt(), view.width, view.height, bottomCornerRadiusPx)
            }
        }
        context.obtainStyledAttributes(attrs, R.styleable.AspectRatioTextureView).use {
            maxWidthPx = it.getDimensionPixelSize(R.styleable.AspectRatioTextureView_android_maxWidth, NO_MAX_WIDTH)
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        if (videoWidth != width || videoHeight != height) {
            videoWidth = width
            videoHeight = height
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (videoWidth == 0 || videoHeight == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec).let { if (maxWidthPx == NO_MAX_WIDTH) it else minOf(it, maxWidthPx) }
        val height = width * videoHeight / videoWidth
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // A fraction of the width, not a fixed dp, so it keeps matching the sketch frame's own
        // corner curve (which stretches with this view) at any size.
        bottomCornerRadiusPx = w * BOTTOM_CORNER_RADIUS_FRACTION
        invalidateOutline()
    }

    companion object {
        // onboarding_add_to_dock_sketch_frame's bottom-right corner: the straight edges stop ~34.67
        // and ~34.54 units short of the corner (vertically/horizontally) before curving — averaged
        // since the hand-traced curve isn't perfectly circular.
        private const val FRAME_CORNER_RADIUS_UNITS = (34.67f + 34.54f) / 2f
        private const val FRAME_VIEWPORT_WIDTH_UNITS = 281f
        private const val BOTTOM_CORNER_RADIUS_FRACTION = FRAME_CORNER_RADIUS_UNITS / FRAME_VIEWPORT_WIDTH_UNITS

        private const val NO_MAX_WIDTH = -1
    }
}
