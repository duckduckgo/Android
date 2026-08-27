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

package com.duckduckgo.common.ui.compose.skeleton

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import kotlin.math.max
import kotlin.math.tan

@Composable
internal fun DaxSkeletonLine(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    DaxSkeletonShape(
        modifier = Modifier
            .height(DaxSkeletonDefaults.LineHeight)
            .then(modifier),
        shape = DuckDuckGoTheme.shapes.medium,
        animated = animated,
    )
}

@Composable
internal fun DaxSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = DaxSkeletonDefaults.CircleSize,
    animated: Boolean = true,
) {
    DaxSkeletonShape(
        modifier = Modifier
            .size(size)
            .then(modifier),
        shape = CircleShape,
        animated = animated,
    )
}

@Composable
private fun DaxSkeletonShape(
    shape: Shape,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseColor = DaxSkeletonDefaults.color
    val restingColor = baseColor.copy(alpha = baseColor.alpha * DaxSkeletonDefaults.RestingAlpha)

    val progress = if (animated) {
        val transition = rememberInfiniteTransition(label = "DaxSkeletonShimmer")
        val animatedProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = DaxSkeletonDefaults.SweepDurationMillis + DaxSkeletonDefaults.SweepDelayMillis
                    0f at 0 using LinearEasing
                    1f at DaxSkeletonDefaults.SweepDurationMillis
                },
            ),
            label = "DaxSkeletonShimmerProgress",
        )
        animatedProgress
    } else {
        DaxSkeletonDefaults.RestingProgress
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val sweepWidth = max(size.width, DaxSkeletonDefaults.MinSweepWidth.toPx())
                val clearance = size.height * tan(DaxSkeletonDefaults.SweepTiltRadians)
                val bandTiltDy = sweepWidth * tan(DaxSkeletonDefaults.SweepTiltRadians)
                val travel = size.width + sweepWidth + clearance
                onDrawBehind {
                    val sweepStart = -sweepWidth + travel * progress
                    val brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to restingColor,
                            0.5f to baseColor,
                            1f to restingColor,
                        ),
                        start = Offset(sweepStart, 0f),
                        end = Offset(sweepStart + sweepWidth, bandTiltDy),
                    )
                    drawRect(brush)
                }
            }
            .clearAndSetSemantics { },
    )
}

internal object DaxSkeletonDefaults {
    val LineHeight: Dp = 16.dp
    val CircleSize: Dp = 40.dp
    const val SweepDurationMillis: Int = 500
    const val SweepDelayMillis: Int = 500
    const val RestingAlpha: Float = 0.3f
    const val RestingProgress: Float = 0.5f
    val SweepTiltRadians: Float = Math.toRadians(20.0).toFloat()
    val MinSweepWidth: Dp = 160.dp

    val color: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.system.lines
}

@PreviewLightDark
@Composable
private fun DaxSkeletonPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DaxSkeletonCircle()
                DaxSkeletonLine(modifier = Modifier.fillMaxWidth())
            }
            DaxSkeletonCircle(size = 16.dp)
            DaxSkeletonLine(modifier = Modifier.width(120.dp))
        }
    }
}
