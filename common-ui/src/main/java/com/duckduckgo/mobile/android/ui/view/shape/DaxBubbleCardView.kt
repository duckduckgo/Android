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

package com.duckduckgo.mobile.android.ui.view.shape

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

class DaxBubbleCardView
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
        val edgeTreatment = DaxBubbleEdgeTreatment(cornerSize, distanceFromEdge)

        background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(cornderRadius)
                .setTopEdge(edgeTreatment)
                .build(),
        ).apply {
            fillColor = ColorStateList.valueOf(context.getColorFromAttr(R.attr.daxColorSurface))
        }
    }
}
