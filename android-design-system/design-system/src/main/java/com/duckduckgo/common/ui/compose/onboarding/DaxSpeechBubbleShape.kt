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

package com.duckduckgo.common.ui.compose.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Geometric tail placement used by [DaxSpeechBubbleShape].
 *
 * The start and end variants are layout-direction relative. The public onboarding tail enum maps to
 * this representation when the dialog is implemented; keeping the offset here avoids exposing
 * arbitrary geometry from the design-system component.
 */
internal sealed class DaxSpeechBubbleTailPosition {
    data object None : DaxSpeechBubbleTailPosition()

    data class FromStart(val offset: Dp) : DaxSpeechBubbleTailPosition()

    data class FromEnd(val offset: Dp) : DaxSpeechBubbleTailPosition()

    data class Interpolated(
        val edgeFraction: Float,
        val offsetDp: Float,
    ) : DaxSpeechBubbleTailPosition()
}

internal data class DaxSpeechBubbleTailPlacement(
    val edgeFraction: Float,
    val offsetDp: Float,
    val present: Boolean,
)

internal fun logicalTailCenter(
    widthPx: Float,
    offsetPx: Float,
    edgeFraction: Float,
): Float = offsetPx + (widthPx - 2f * offsetPx) * edgeFraction

/**
 * Speech-bubble card shape with the onboarding tail cut into its bottom edge.
 *
 * The tail is the nine-cubic path from the legacy DaxBubbleBottomEdgeTreatment, scaled by [depth].
 * A 33.dp tail band is reserved inside the shape; the legacy path itself is 30.dp high. Tail
 * positions are layout-direction relative and are mirrored in RTL.
 */
internal class DaxSpeechBubbleShape(
    private val cornerRadius: Dp,
    private val tailPosition: DaxSpeechBubbleTailPosition = DaxSpeechBubbleTailPosition.None,
    private val depth: Float = 1f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        with(density) {
            val tailBandHeight = if (tailPosition is DaxSpeechBubbleTailPosition.None) {
                0f
            } else {
                DaxOnboardingDialogDefaults.TailBandHeight.toPx()
            }
            val bottom = size.height - tailBandHeight
            val radius = cornerRadius.toPx().coerceAtMost(minOf(size.width, bottom) / 2f).coerceAtLeast(0f)

            val path = Path()
            path.moveTo(radius, 0f)
            path.lineTo(size.width - radius, 0f)
            path.arcTo(
                rect = Rect(size.width - 2f * radius, 0f, size.width, 2f * radius),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            path.lineTo(size.width, bottom - radius)
            path.arcTo(
                rect = Rect(size.width - 2f * radius, bottom - 2f * radius, size.width, bottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )

            val depthFraction = depth.coerceIn(0f, 1f)
            val scale = TailHeight.toPx() / TailHeight.value * depthFraction
            val arrowWidth = TailWidth.value * scale
            // View hosts such as ConstraintLayout can probe at zero width; a tail that doesn't fit has no valid position.
            if (tailPosition !is DaxSpeechBubbleTailPosition.None && depthFraction > 0f && arrowWidth <= size.width) {
                val center = when (tailPosition) {
                    is DaxSpeechBubbleTailPosition.FromStart -> radius + tailPosition.offset.toPx()
                    is DaxSpeechBubbleTailPosition.FromEnd -> size.width - radius - tailPosition.offset.toPx()
                    is DaxSpeechBubbleTailPosition.Interpolated -> logicalTailCenter(
                        widthPx = size.width,
                        offsetPx = radius + tailPosition.offsetDp.dp.toPx(),
                        edgeFraction = tailPosition.edgeFraction,
                    )
                    DaxSpeechBubbleTailPosition.None -> 0f
                }.coerceIn(arrowWidth / 2f, size.width - arrowWidth / 2f)

                // Material's bottom edge runs right-to-left. Mapping its local x axis this way keeps
                // the original asymmetric hook while making the logical start/end position explicit.
                fun tailX(x: Float): Float = center + arrowWidth / 2f - x * scale
                fun tailY(y: Float): Float = bottom - y * scale

                path.lineTo(tailX(0f), bottom)
                TailCurves.forEach { curve ->
                    path.cubicTo(
                        x1 = tailX(curve.control1X),
                        y1 = tailY(curve.control1Y),
                        x2 = tailX(curve.control2X),
                        y2 = tailY(curve.control2Y),
                        x3 = tailX(curve.endX),
                        y3 = tailY(curve.endY),
                    )
                }
                path.lineTo(tailX(TailWidth.value), bottom)
            } else {
                path.lineTo(radius, bottom)
            }

            path.arcTo(
                rect = Rect(0f, bottom - 2f * radius, 2f * radius, bottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            path.lineTo(0f, radius)
            path.arcTo(
                rect = Rect(0f, 0f, 2f * radius, 2f * radius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            path.close()

            if (layoutDirection == LayoutDirection.Rtl) {
                path.transform(
                    Matrix().apply {
                        resetToPivotedTransform(
                            pivotX = size.width / 2f,
                            scaleX = -1f,
                        )
                    },
                )
            }

            return Outline.Generic(path)
        }
    }

    private data class Curve(
        val control1X: Float,
        val control1Y: Float,
        val control2X: Float,
        val control2Y: Float,
        val endX: Float,
        val endY: Float,
    )

    companion object {
        val TailHeight = 30.dp
        val TailWidth = 47.14058.dp

        private val TailCurves = listOf(
            Curve(2.8355f, 0f, 4.9409f, -1.32054f, 6.8544f, -3.33789f),
            Curve(8.7314f, -5.31666f, 10.5271f, -8.08434f, 12.6835f, -11.06444f),
            Curve(17.0304f, -17.07144f, 23.1365f, -24.39164f, 35.3339f, -29.80464f),
            Curve(36.846f, -30.47574f, 38.3232f, -30.09324f, 39.3369f, -29.13864f),
            Curve(40.3586f, -28.17644f, 40.9016f, -26.63464f, 40.4628f, -24.99804f),
            Curve(39.6477f, -21.95764f, 38.7778f, -18.57714f, 38.1083f, -15.63474f),
            Curve(37.4462f, -12.72454f, 36.9582f, -10.15444f, 36.9453f, -8.78514f),
            Curve(36.9208f, -6.20757f, 38.2915f, -3.99944f, 40.2158f, -2.46093f),
            Curve(42.1375f, -0.92451f, 44.6734f, 0f, TailWidth.value, 0f),
        )
    }
}

@Composable
@PreviewLightDark
private fun DaxSpeechBubbleShapePreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeechBubblePreviewItem("Flat", DaxSpeechBubbleTailPosition.None, 0f)
            SpeechBubblePreviewItem("Start 80dp · flat", DaxSpeechBubbleTailPosition.FromStart(80.dp), 0f)
            SpeechBubblePreviewItem("Start 80dp · half", DaxSpeechBubbleTailPosition.FromStart(80.dp), 0.5f)
            SpeechBubblePreviewItem("Start 96dp · full", DaxSpeechBubbleTailPosition.FromStart(96.dp), 1f)
            SpeechBubblePreviewItem("End 80dp · half", DaxSpeechBubbleTailPosition.FromEnd(80.dp), 0.5f)
            SpeechBubblePreviewItem("End 80dp · flat", DaxSpeechBubbleTailPosition.FromEnd(80.dp), 0f)
            SpeechBubblePreviewItem("End 80dp · full", DaxSpeechBubbleTailPosition.FromEnd(80.dp), 1f)
        }
    }
}

@Composable
private fun SpeechBubblePreviewItem(
    label: String,
    tailPosition: DaxSpeechBubbleTailPosition,
    depth: Float,
) {
    Column {
        DaxText(
            text = label,
            color = DuckDuckGoTheme.colors.text.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        DaxSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            shape = DaxSpeechBubbleShape(
                cornerRadius = 36.dp,
                tailPosition = tailPosition,
                depth = depth,
            ),
            shadowElevation = 0.dp,
        ) {}
    }
}
