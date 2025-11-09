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

package com.duckduckgo.common.ui.view.shape

import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath

/**
 * Instantiates a speech bubble triangle treatment scaled based on the given height
 *
 * @param heightPx the width of the triangle in pixels. The triangle will scale appropriately on the horizontal axis.
 */
class DaxBubbleTopEdgeTreatment(
    private val heightPx: Int = ORIGINAL_TOP_ARROW_HEIGHT_DP.toPx(),
) : EdgeTreatment() {

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val scaleFactor = heightPx.toFloat() / ORIGINAL_TOP_ARROW_HEIGHT_DP
        val arrowWidth = 31.077f * scaleFactor
        val arrowStart = center - (arrowWidth / 2)

        shapePath.lineTo(arrowStart, 0f)
        shapePath.cubicToPoint(
            arrowStart + 0.575912f * scaleFactor,
            0f,
            arrowStart - 3.25607f * scaleFactor,
            -8.2492f,
            arrowStart + 10.3556f * scaleFactor,
            -19.802755f * scaleFactor,
        )
        shapePath.cubicToPoint(
            arrowStart + 10.8162f * scaleFactor,
            -19.806305f * scaleFactor,
            arrowStart + 11.6186f * scaleFactor,
            -19.969095f * scaleFactor,
            arrowStart + 11.7322f * scaleFactor,
            -19.43113f * scaleFactor,
        )
        shapePath.cubicToPoint(
            arrowStart + 14.3754f * scaleFactor,
            -6.9172f * scaleFactor,
            arrowStart + 22.017f * scaleFactor,
            -2.327f * scaleFactor,
            arrowStart + 25.9858f * scaleFactor,
            -0.7546f * scaleFactor,
        )
        shapePath.cubicToPoint(
            arrowStart + 27.5652f * scaleFactor,
            -0.1288f * scaleFactor,
            arrowStart + 29.326f * scaleFactor,
            0f,
            arrowStart + arrowWidth,
            0f,
        )
        shapePath.lineTo(arrowStart + arrowWidth, 0f)
    }

    companion object {
        const val ORIGINAL_TOP_ARROW_HEIGHT_DP = 20
    }
}
