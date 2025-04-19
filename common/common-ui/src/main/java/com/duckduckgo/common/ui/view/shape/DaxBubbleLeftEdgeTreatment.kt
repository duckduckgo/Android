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
 * @param heightPx the height of the triangle in pixels. The triangle will scale appropriately on
 *   the horizontal axis. Remember, EdgeTreatment draw from 0,0 based on **it's** edge. So when
 *   drawing for the left edge for example, you can imagine rotating 90 degrees clockwise, and then
 *   drawing as usual from 0,0.
 */
class DaxBubbleLeftEdgeTreatment(
    private val heightPx: Int = ORIGINAL_LEFT_ARROW_HEIGHT_DP.toPx(),
) : EdgeTreatment() {

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val scaleFactor = heightPx.toFloat() / ORIGINAL_LEFT_ARROW_HEIGHT_DP
        val arrowWidth = 31.077f * scaleFactor
        val arrowStart = center - arrowWidth

        shapePath.lineTo(arrowStart + 0.5f * scaleFactor, 0f)

        shapePath.lineTo(arrowStart + 0.98455f * scaleFactor, 0f)

        shapePath.cubicToPoint(
            arrowStart + 2.88179f * scaleFactor,
            0f,
            arrowStart + 4.66321f * scaleFactor,
            -1.4034f * scaleFactor,
            arrowStart + 5.8606f * scaleFactor,
            -2.8729f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 9.44567f * scaleFactor,
            -7.2728f * scaleFactor,
            arrowStart + 17.8613f * scaleFactor,
            -16.9229f * scaleFactor,
            arrowStart + 28.0202f * scaleFactor,
            -23.9878f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 30.3633f * scaleFactor,
            -25.617231f * scaleFactor,
            arrowStart + 33.1369f * scaleFactor,
            -23.07663f * scaleFactor,
            arrowStart + 32.3664f * scaleFactor,
            -20.33124f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 29.2211f * scaleFactor,
            -9.1238f * scaleFactor,
            arrowStart + 33.1369f * scaleFactor,
            -3.7184f * scaleFactor,
            arrowStart + 35.4809f * scaleFactor,
            -1.6164f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 36.4049f * scaleFactor,
            -0.7877f * scaleFactor,
            arrowStart + 37.6466f * scaleFactor,
            0f,
            arrowStart + 38.8885f * scaleFactor,
            0f,
        )

        shapePath.lineTo(arrowStart + 39.5f * scaleFactor, 0f)
    }

    companion object {
        const val ORIGINAL_LEFT_ARROW_HEIGHT_DP = 24
    }
}
