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
open class DaxBubbleBottomEdgeTreatment(
    protected val heightPx: Int = ORIGINAL_BOTTOM_ARROW_HEIGHT_DP.toPx(),
) : EdgeTreatment() {

    var depthFraction: Float = 1f

    /**
     * The tail is asymmetric — it hooks towards one side — so a card whose artwork sits on the opposite side
     * needs the shape reflected rather than just repositioned.
     */
    var mirrored: Boolean = false

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val scaleFactor = (heightPx.toFloat() / ORIGINAL_BOTTOM_ARROW_HEIGHT_DP) * depthFraction
        val arrowWidth = ORIGINAL_BOTTOM_ARROW_WIDTH_DP * scaleFactor
        val arrowStart = center - (arrowWidth / 2)

        shapePath.lineTo(arrowStart, 0f)

        (if (mirrored) MIRRORED_TAIL else TAIL).forEach { curve ->
            shapePath.cubicToPoint(
                arrowStart + curve.control1X * scaleFactor,
                curve.control1Y * scaleFactor,
                arrowStart + curve.control2X * scaleFactor,
                curve.control2Y * scaleFactor,
                arrowStart + curve.endX * scaleFactor,
                curve.endY * scaleFactor,
            )
        }

        shapePath.lineTo(arrowStart + arrowWidth, 0f)
    }

    /** One cubic of the tail outline, in unscaled dp relative to the tail's leading edge. */
    private class Curve(
        val control1X: Float,
        val control1Y: Float,
        val control2X: Float,
        val control2Y: Float,
        val endX: Float,
        val endY: Float,
    )

    companion object {
        const val ORIGINAL_BOTTOM_ARROW_HEIGHT_DP = 30
        const val ORIGINAL_BOTTOM_ARROW_WIDTH_DP = 47.14058f

        private val TAIL = listOf(
            Curve(2.8355f, 0f, 4.9409f, -1.32054f, 6.8544f, -3.33789f),
            Curve(8.7314f, -5.31666f, 10.5271f, -8.08434f, 12.6835f, -11.06444f),
            Curve(17.0304f, -17.07144f, 23.1365f, -24.39164f, 35.3339f, -29.80464f),
            Curve(36.846f, -30.47574f, 38.3232f, -30.09324f, 39.3369f, -29.13864f),
            Curve(40.3586f, -28.17644f, 40.9016f, -26.63464f, 40.4628f, -24.99804f),
            Curve(39.6477f, -21.95764f, 38.7778f, -18.57714f, 38.1083f, -15.63474f),
            Curve(37.4462f, -12.72454f, 36.9582f, -10.15444f, 36.9453f, -8.78514f),
            Curve(36.9208f, -6.20757f, 38.2915f, -3.99944f, 40.2158f, -2.46093f),
            Curve(42.1375f, -0.92451f, 44.6734f, 0f, ORIGINAL_BOTTOM_ARROW_WIDTH_DP, 0f),
        )

        /**
         * Reflected about the tail's vertical centre. The edge path has to stay left-to-right, so the curves
         * are walked backwards: each one runs from its own end point to its predecessor's, which swaps its
         * control points too.
         */
        private val MIRRORED_TAIL = TAIL.indices.reversed().map { index ->
            val curve = TAIL[index]
            val start = if (index == 0) null else TAIL[index - 1]
            Curve(
                control1X = ORIGINAL_BOTTOM_ARROW_WIDTH_DP - curve.control2X,
                control1Y = curve.control2Y,
                control2X = ORIGINAL_BOTTOM_ARROW_WIDTH_DP - curve.control1X,
                control2Y = curve.control1Y,
                endX = ORIGINAL_BOTTOM_ARROW_WIDTH_DP - (start?.endX ?: 0f),
                endY = start?.endY ?: 0f,
            )
        }
    }
}
