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

import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath

/**
 * Draws a speech bubble tail on the bottom edge of a card.
 *
 * All coordinates scale proportionally so the tail maintains its shape at any size.
 *
 * @param heightPx the height of the tail in pixels. The tail will scale proportionally on
 *   both axes.
 */
class DaxBubbleBottomEdgeTreatment(
    private val heightPx: Int = ORIGINAL_BOTTOM_ARROW_HEIGHT_DP.toPx(),
) : EdgeTreatment() {

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val scaleFactor = heightPx.toFloat() / ORIGINAL_BOTTOM_ARROW_HEIGHT_DP
        val arrowWidth = 47.14058f * scaleFactor
        val arrowStart = center - (arrowWidth / 2)

        shapePath.lineTo(arrowStart, 0f)

        shapePath.cubicToPoint(
            arrowStart + 2.8355f * scaleFactor,
            0f,
            arrowStart + 4.9409f * scaleFactor,
            -1.32054f * scaleFactor,
            arrowStart + 6.8544f * scaleFactor,
            -3.33789f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 8.7314f * scaleFactor,
            -5.31666f * scaleFactor,
            arrowStart + 10.5271f * scaleFactor,
            -8.08434f * scaleFactor,
            arrowStart + 12.6835f * scaleFactor,
            -11.06444f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 17.0304f * scaleFactor,
            -17.07144f * scaleFactor,
            arrowStart + 23.1365f * scaleFactor,
            -24.39164f * scaleFactor,
            arrowStart + 35.3339f * scaleFactor,
            -29.80464f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 36.846f * scaleFactor,
            -30.47574f * scaleFactor,
            arrowStart + 38.3232f * scaleFactor,
            -30.09324f * scaleFactor,
            arrowStart + 39.3369f * scaleFactor,
            -29.13864f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 40.3586f * scaleFactor,
            -28.17644f * scaleFactor,
            arrowStart + 40.9016f * scaleFactor,
            -26.63464f * scaleFactor,
            arrowStart + 40.4628f * scaleFactor,
            -24.99804f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 39.6477f * scaleFactor,
            -21.95764f * scaleFactor,
            arrowStart + 38.7778f * scaleFactor,
            -18.57714f * scaleFactor,
            arrowStart + 38.1083f * scaleFactor,
            -15.63474f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 37.4462f * scaleFactor,
            -12.72454f * scaleFactor,
            arrowStart + 36.9582f * scaleFactor,
            -10.15444f * scaleFactor,
            arrowStart + 36.9453f * scaleFactor,
            -8.78514f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 36.9208f * scaleFactor,
            -6.20757f * scaleFactor,
            arrowStart + 38.2915f * scaleFactor,
            -3.99944f * scaleFactor,
            arrowStart + 40.2158f * scaleFactor,
            -2.46093f * scaleFactor,
        )

        shapePath.cubicToPoint(
            arrowStart + 42.1375f * scaleFactor,
            -0.92451f * scaleFactor,
            arrowStart + 44.6734f * scaleFactor,
            0f,
            arrowStart + arrowWidth,
            0f,
        )

        shapePath.lineTo(arrowStart + arrowWidth, 0f)
    }

    companion object {
        const val ORIGINAL_BOTTOM_ARROW_HEIGHT_DP = 30
    }
}
