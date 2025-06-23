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

import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath

/**
 * Instantiates a speech bubble triangle treatment scaled based on the given height
 *
 * @param heightPx the width of the triangle in pixels. The triangle will scale appropriately on the horizontal axis.
 */
class BBTopEdgeTreatment(
    private val heightPx: Int = ORIGINAL_TOP_ARROW_HEIGHT_DP.toPx(),
) : EdgeTreatment() {

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val scaleFactor = heightPx.toFloat() / ORIGINAL_TOP_ARROW_HEIGHT_DP

        shapePath.lineTo(
            0f * scaleFactor,
            0f * scaleFactor
        )

        shapePath.cubicToPoint(
            21.2893f * scaleFactor,  0f          * scaleFactor,
            11.5702f * scaleFactor, -24f         * scaleFactor,
            11.5702f * scaleFactor, -24f         * scaleFactor
        )

        shapePath.cubicToPoint(
            11.5702f * scaleFactor, -24f         * scaleFactor,
            38.4132f * scaleFactor, -22.6063f    * scaleFactor,
            56f       * scaleFactor,  0f         * scaleFactor
        )

        shapePath.lineTo(
            0f * scaleFactor,
            0f * scaleFactor
        )
    }

    companion object {
        const val ORIGINAL_TOP_ARROW_HEIGHT_DP = 24
    }
}

