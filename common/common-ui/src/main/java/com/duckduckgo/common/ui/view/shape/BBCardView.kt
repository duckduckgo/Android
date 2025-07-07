/*
 * Copyright (c) 2025 DuckDuckGo
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
import android.util.AttributeSet
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel

class BBCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.cardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        elevation = 0f

        setCardBackgroundColor(context.getColor(R.color.bbSurfaceColor))

        val edgeTreatment = BBTopEdgeTreatment()
        val offsetEdgeTreatment = OffsetStartTreatment(
            other = edgeTreatment,
            offsetPx = 56.toPx(),
        )

        val shapeBuilder = ShapeAppearanceModel.builder()

        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.BBCardView, defStyleAttr, 0)
        val defaultCornerSize = 56.toPx() * 1f

        shapeAppearanceModel = shapeBuilder.apply {
            setAllCornerSizes(defaultCornerSize)

            if (attr.hasValue(R.styleable.BBCardView_topRightCornerRadius)) {
                setTopRightCornerSize(attr.getDimension(R.styleable.BBCardView_topRightCornerRadius, 0f))
            }

            setTopEdge(offsetEdgeTreatment)
        }.build()
    }
}
