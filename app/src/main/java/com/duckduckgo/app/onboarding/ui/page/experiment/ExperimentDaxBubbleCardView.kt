/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.experiment

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.ShapePath

class ExperimentDaxBubbleCardView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.cardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        val cornderRadius = resources.getDimension(R.dimen.mediumShapeCornerRadius)
        val cornerSize = resources.getDimension(R.dimen.daxBubbleDialogEdge)
        val distanceFromEdge = resources.getDimension(R.dimen.daxBubbleDialogDistanceFromEdge)
        val edgeTreatment = ExperimentDaxBubbleEdgeTreatment(cornerSize, distanceFromEdge)

        setCardBackgroundColor(ColorStateList.valueOf(context.getColorFromAttr(R.attr.daxColorSurface)))
        shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornderRadius)
            .setLeftEdge(edgeTreatment)
            .build()
    }

    class ExperimentDaxBubbleEdgeTreatment(
        private val size: Float,
        private val distanceFromEdge: Float,
    ) : EdgeTreatment() {
        override fun getEdgePath(
            length: Float,
            center: Float,
            interpolation: Float,
            shapePath: ShapePath,
        ) {
            val d = length - distanceFromEdge
            shapePath.lineTo(d - size * interpolation, 0f)
            shapePath.lineTo(d, -size * interpolation)
            shapePath.lineTo(d + size * interpolation, 0f)
            shapePath.lineTo(length, 0f)
        }
    }
}
