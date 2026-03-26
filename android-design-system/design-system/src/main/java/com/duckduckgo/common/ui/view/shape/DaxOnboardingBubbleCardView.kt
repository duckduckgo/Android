/*
 * Copyright (c) 2022 DuckDuckGo
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
import android.util.AttributeSet
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapeAppearanceModel

class DaxOnboardingBubbleCardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.cardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.DaxOnboardingBubbleCardView, defStyleAttr, 0)
        val edgePosition = EdgePosition.from(attr.getInt(R.styleable.DaxOnboardingBubbleCardView_edgePosition, 0))
        val arrowHeightPx = attr.getDimensionPixelSize(
            R.styleable.DaxOnboardingBubbleCardView_arrowHeight,
            when (edgePosition) {
                EdgePosition.TOP -> DaxBubbleTopEdgeTreatment.ORIGINAL_TOP_ARROW_HEIGHT_DP.toPx()
                EdgePosition.LEFT -> DaxBubbleLeftEdgeTreatment.ORIGINAL_LEFT_ARROW_HEIGHT_DP.toPx()
            },
        )
        val offsetStart = attr.getDimensionPixelSize(R.styleable.DaxOnboardingBubbleCardView_arrowOffsetStart, 0)
        val offsetEnd = attr.getDimensionPixelSize(R.styleable.DaxOnboardingBubbleCardView_arrowOffsetEnd, 0)

        if (offsetStart != 0 && offsetEnd != 0) {
            throw IllegalArgumentException("Only one of arrowOffsetStart or arrowOffsetEnd can be set")
        }

        val cornerRadius = resources.getDimension(R.dimen.dax_onboarding_bubble_card_view_corner_radius)

        setCardBackgroundColor(ColorStateList.valueOf(context.getColorFromAttr(R.attr.daxColorSurface)))

        val edgeTreatment = when (edgePosition) {
            EdgePosition.TOP -> DaxBubbleTopEdgeTreatment(heightPx = arrowHeightPx)
            EdgePosition.LEFT -> DaxBubbleLeftEdgeTreatment(heightPx = arrowHeightPx)
        }

        val offsetEdgeTreatment = applyOffsetEdgeTreatment(offsetStart, offsetEnd, edgeTreatment)

        shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .apply {
                when (edgePosition) {
                    EdgePosition.TOP -> setTopEdge(offsetEdgeTreatment)
                    EdgePosition.LEFT -> setLeftEdge(offsetEdgeTreatment)
                }
            }
            .build()
    }

    private fun applyOffsetEdgeTreatment(offsetStart: Int, offsetEnd: Int, edgeTreatment: EdgeTreatment): EdgeTreatment {
        return if (offsetStart != 0) {
            OffsetStartTreatment(edgeTreatment, offsetStart.toPx())
        } else if (offsetEnd != 0) {
            OffsetEndTreatment(edgeTreatment, offsetEnd.toPx())
        } else {
            edgeTreatment
        }
    }

    enum class EdgePosition {
        TOP,
        LEFT,
        ;

        companion object {
            fun from(value: Int): EdgePosition {
                // same order as attrs-dax-dialog.xml
                return when (value) {
                    1 -> LEFT
                    else -> TOP
                }
            }
        }
    }
}
