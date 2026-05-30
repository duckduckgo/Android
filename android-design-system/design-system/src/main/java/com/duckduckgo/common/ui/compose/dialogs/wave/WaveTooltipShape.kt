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

package com.duckduckgo.common.ui.compose.dialogs.wave

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

class DynamicWaveShape(
    private val edge: WaveEdge,
    private val position: Float, // 0f to 1f
    private val waveBaseWidthDp: Dp,
    private val waveAmplitudeDp: Dp,
    private val cornerRadiusDp: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()

        density.run {
            val waveBase = waveBaseWidthDp.toPx()
            val waveAmp = waveAmplitudeDp.toPx()
            val radius = cornerRadiusDp.toPx()

            // Defines how soft the tip of the tail should be
            val roundness = waveBase * 0.15f

            // Define the inner rectangle bounds
            val rectTop = if (edge == WaveEdge.Top) waveAmp else 0f
            val rectBottom = if (edge == WaveEdge.Bottom) size.height - waveAmp else size.height
            val rectLeft = if (edge == WaveEdge.Left) waveAmp else 0f
            val rectRight = if (edge == WaveEdge.Right) size.width - waveAmp else size.width

            path.moveTo(rectLeft, rectTop + radius)
            path.quadraticTo(rectLeft, rectTop, rectLeft + radius, rectTop)

            // --- TOP EDGE (Drawing Left to Right) ---
            if (edge == WaveEdge.Top) {
                val minCenter = rectLeft + radius + waveBase / 2
                val maxCenter = rectRight - radius - waveBase / 2
                val center = minCenter + position * (maxCenter - minCenter)

                val startX = center - waveBase / 2
                val endX = center + waveBase / 2
                val tipX = startX
                val tipY = 0f
                val baseY = rectTop

                path.lineTo(startX, baseY)

                // Steep inner S-curve ending BEFORE the absolute tip
                path.cubicTo(
                    startX + waveBase * 0.2f, baseY - waveAmp * 0.2f,
                    tipX + waveBase * 0.1f, tipY + waveAmp * 0.4f,
                    tipX + roundness * 0.2f, tipY + roundness // Stop early
                )

                // The rounded tip curving around the sharp corner
                path.quadraticTo(
                    tipX, tipY, // Control point is the sharp corner
                    tipX + roundness, tipY + roundness * 0.1f // Exit point
                )

                // Long outer sweep starting AFTER the tip
                path.cubicTo(
                    tipX + waveBase * 0.5f, tipY, // Adjusted CP1 to match new start
                    endX - waveBase * 0.1f, baseY - waveAmp * 0.1f,
                    endX, baseY
                )
            }
            path.lineTo(rectRight - radius, rectTop)
            path.quadraticTo(rectRight, rectTop, rectRight, rectTop + radius)

            // --- RIGHT EDGE (Drawing Top to Bottom) ---
            if (edge == WaveEdge.Right) {
                val minCenter = rectTop + radius + waveBase / 2
                val maxCenter = rectBottom - radius - waveBase / 2
                val center = minCenter + position * (maxCenter - minCenter)

                val startY = center - waveBase / 2
                val endY = center + waveBase / 2
                val tipY = startY
                val tipX = size.width
                val baseX = rectRight

                path.lineTo(baseX, startY)

                path.cubicTo(
                    baseX + waveAmp * 0.2f, startY + waveBase * 0.2f,
                    tipX - waveAmp * 0.4f, tipY + waveBase * 0.1f,
                    tipX - roundness, tipY + roundness * 0.2f // Stop early
                )

                path.quadraticTo(
                    tipX, tipY,
                    tipX - roundness * 0.1f, tipY + roundness // Exit point
                )

                path.cubicTo(
                    tipX, tipY + waveBase * 0.5f,
                    baseX + waveAmp * 0.1f, endY - waveBase * 0.1f,
                    baseX, endY
                )
            }
            path.lineTo(rectRight, rectBottom - radius)
            path.quadraticTo(rectRight, rectBottom, rectRight - radius, rectBottom)

            // --- BOTTOM EDGE (Drawing Right to Left) ---
            if (edge == WaveEdge.Bottom) {
                val minCenter = rectLeft + radius + waveBase / 2
                val maxCenter = rectRight - radius - waveBase / 2
                val center = minCenter + position * (maxCenter - minCenter)

                val startX = center + waveBase / 2
                val endX = center - waveBase / 2
                val tipX = endX
                val tipY = size.height
                val baseY = rectBottom

                path.lineTo(startX, baseY)

                path.cubicTo(
                    startX - waveBase * 0.1f, baseY + waveAmp * 0.1f,
                    tipX + waveBase * 0.5f, tipY,
                    tipX + roundness, tipY - roundness * 0.1f // Stop early
                )

                path.quadraticTo(
                    tipX, tipY,
                    tipX + roundness * 0.2f, tipY - roundness // Exit point
                )

                path.cubicTo(
                    tipX + waveBase * 0.1f, tipY - waveAmp * 0.4f,
                    endX + waveBase * 0.2f, baseY + waveAmp * 0.2f,
                    endX, baseY
                )
            }
            path.lineTo(rectLeft + radius, rectBottom)
            path.quadraticTo(rectLeft, rectBottom, rectLeft, rectBottom - radius)

            // --- LEFT EDGE (Drawing Bottom to Top) ---
            if (edge == WaveEdge.Left) {
                val minCenter = rectTop + radius + waveBase / 2
                val maxCenter = rectBottom - radius - waveBase / 2
                val center = minCenter + position * (maxCenter - minCenter)

                val startY = center + waveBase / 2
                val endY = center - waveBase / 2
                val tipY = endY
                val tipX = 0f
                val baseX = rectLeft

                path.lineTo(baseX, startY)

                path.cubicTo(
                    baseX - waveAmp * 0.1f, startY - waveBase * 0.1f,
                    tipX, tipY + waveBase * 0.5f,
                    tipX + roundness * 0.1f, tipY + roundness // Stop early
                )

                path.quadraticTo(
                    tipX, tipY,
                    tipX + roundness, tipY + roundness * 0.2f // Exit point
                )

                path.cubicTo(
                    tipX + waveAmp * 0.4f, tipY + waveBase * 0.1f,
                    baseX - waveAmp * 0.2f, endY + waveBase * 0.2f,
                    baseX, endY
                )
            }
            path.lineTo(rectLeft, rectTop + radius)
            path.close()
        }
        return Outline.Generic(path)
    }
}
