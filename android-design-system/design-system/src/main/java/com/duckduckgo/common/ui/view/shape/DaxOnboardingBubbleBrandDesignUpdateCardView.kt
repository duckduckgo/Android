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

package com.duckduckgo.common.ui.view.shape

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapeAppearanceModel

class DaxOnboardingBubbleBrandDesignUpdateCardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.cardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private var animatableEdgeTreatment: AnimatableOffsetEdgeTreatment? = null
    private val cornerRadius: Float

    init {
        val attr = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.DaxOnboardingBubbleBrandDesignUpdateCardView,
            defStyleAttr,
            0,
        )
        val offsetStart = attr.getDimensionPixelSize(
            R.styleable.DaxOnboardingBubbleBrandDesignUpdateCardView_arrowOffsetStart,
            0,
        )
        val offsetEnd = attr.getDimensionPixelSize(
            R.styleable.DaxOnboardingBubbleBrandDesignUpdateCardView_arrowOffsetEnd,
            0,
        )
        attr.recycle()

        if (offsetStart != 0 && offsetEnd != 0) {
            throw IllegalArgumentException("Only one of arrowOffsetStart or arrowOffsetEnd can be set")
        }

        cornerRadius = resources.getDimension(R.dimen.dax_brand_design_bubble_card_view_corner_radius)

        setCardBackgroundColor(ColorStateList.valueOf(context.getColorFromAttr(R.attr.onboardingSurfaceTertiary)))
        setStrokeColor(ColorStateList.valueOf(context.getColorFromAttr(R.attr.onboardingAccentAltPrimary)))
        strokeWidth = resources.getDimensionPixelSize(R.dimen.dax_brand_design_bubble_stroke_width)

        val edgeTreatment = DaxBubbleBottomEdgeTreatment()
        val offsetEdgeTreatment = if (offsetStart != 0) {
            AnimatableOffsetEdgeTreatment(edgeTreatment, offsetStart.toFloat()).also {
                animatableEdgeTreatment = it
            }
        } else {
            applyOffsetEdgeTreatment(offsetStart, offsetEnd, edgeTreatment)
        }

        shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .setBottomEdge(offsetEdgeTreatment)
            .build()

        cardElevation = resources.getDimension(R.dimen.dax_brand_design_bubble_card_elevation)

        if (Build.VERSION.SDK_INT >= 28) {
            outlineAmbientShadowColor = ContextCompat.getColor(context, R.color.onboardingBubbleShadowColor)
            outlineSpotShadowColor = ContextCompat.getColor(context, R.color.onboardingBubbleShadowColor)
        }
    }

    /**
     * Clips the first child (typically a ScrollView) to a rounded-rect outline matching the
     * card's corner radius. This prevents foreground-drawn elements like scrollbars from
     * rendering outside the rounded corners.
     *
     * The card itself uses a non-convex [ShapeAppearanceModel] (due to the bubble tail), so
     * Android's [View.setClipToOutline] cannot clip to the full card shape. Applying a
     * simple rounded-rect outline to the child works because its shape IS convex.
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        getChildAt(0)?.apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
            clipToOutline = true
        }
    }

    /**
     * Ensures the bottom margin is large enough to prevent the bubble tail arrow from being
     * clipped by adjacent views. If the current bottom margin is smaller than the arrow height
     * plus the stroke width, it is increased automatically.
     *
     * This triggers a re-layout when the margin is adjusted.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (layoutParams as? MarginLayoutParams)?.let { params ->
            val minMargin = DaxBubbleBottomEdgeTreatment.ORIGINAL_BOTTOM_ARROW_HEIGHT_DP.toPx() + strokeWidth
            if (params.bottomMargin < minMargin) {
                params.bottomMargin = minMargin
                layoutParams = params
            }
        }
    }

    /**
     * Set the target position for the arrow animation.
     *
     * @param offsetFromEndPx visual offset from the right/end edge in pixels.
     */
    fun setArrowAnimationTarget(offsetFromEndPx: Float) {
        animatableEdgeTreatment?.offsetFromEndPx = offsetFromEndPx
    }

    /**
     * Drive the arrow animation.
     *
     * @param fraction 0 = arrow at initial [arrowOffsetStart] position,
     *   1 = arrow at target position set via [setArrowAnimationTarget].
     */
    fun setArrowAnimationFraction(fraction: Float) {
        animatableEdgeTreatment?.fraction = fraction
        // Re-assign shapeAppearanceModel to force MaterialShapeDrawable to
        // mark its cached path as dirty. A plain invalidate() only triggers
        // draw(), but the drawable skips getEdgePath() unless pathDirty is set.
        shapeAppearanceModel = shapeAppearanceModel
    }

    /**
     * The treatment classes are inverted here because Material draws the bottom edge right-to-left
     * (clockwise path), so x=0 in edge-local space is the RIGHT side of the view. This means
     * [OffsetStartTreatment] (center=offsetPx, i.e. near x=0) visually offsets from the end,
     * and [OffsetEndTreatment] (center=length-offsetPx) visually offsets from the start.
     *
     * The old [DaxOnboardingBubbleCardView] uses setTopEdge where x=0 is the left, so the
     * mapping is not inverted there.
     */
    private fun applyOffsetEdgeTreatment(
        offsetStart: Int,
        offsetEnd: Int,
        edgeTreatment: EdgeTreatment,
    ): EdgeTreatment {
        return if (offsetStart != 0) {
            OffsetEndTreatment(edgeTreatment, offsetStart)
        } else if (offsetEnd != 0) {
            OffsetStartTreatment(edgeTreatment, offsetEnd)
        } else {
            edgeTreatment
        }
    }
}
