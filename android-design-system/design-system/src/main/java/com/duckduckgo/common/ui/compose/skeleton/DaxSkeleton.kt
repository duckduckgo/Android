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
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Composable
internal fun DaxSkeletonLine(
    modifier: Modifier = Modifier,
) {
    DaxSkeletonShape(
        modifier = modifier.height(DaxSkeletonDefaults.LineHeight),
        shape = DuckDuckGoTheme.shapes.medium,
    )
}

@Composable
internal fun DaxSkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = DaxSkeletonDefaults.CircleSize,
) {
    DaxSkeletonShape(
        modifier = modifier.size(size),
        shape = CircleShape,
    )
}

@Composable
private fun DaxSkeletonShape(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color = DaxSkeletonDefaults.color, shape = shape)
            .clearAndSetSemantics { },
    )
}

/** Applies one shimmer phase and coordinate system to every skeleton shape in this subtree. */
@Composable
internal fun Modifier.daxSkeletonShimmer(animated: Boolean): Modifier {
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

    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()

        val tilt = DaxSkeletonDefaults.SweepTiltRadians
        val tiltCos = cos(tilt)
        val tiltSin = sin(tilt)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val travel = size.width + tan(tilt) * size.height
        val translationX = -travel + 2f * travel * progress
        val gradientStart = Offset(
            x = centerX * (1f - tiltCos) + centerY * tiltSin + translationX,
            y = centerY * (1f - tiltCos) - centerX * tiltSin,
        )
        val gradientEnd = Offset(
            x = centerX * (1f + tiltCos) + centerY * tiltSin + translationX,
            y = centerY * (1f - tiltCos) + centerX * tiltSin,
        )
        val restingMask = Color.White.copy(alpha = DaxSkeletonDefaults.RestingAlpha)
        val brush = Brush.linearGradient(
            colorStops = arrayOf(
                DaxSkeletonDefaults.BaseMaskStart to restingMask,
                DaxSkeletonDefaults.HighlightMaskStart to Color.White,
                DaxSkeletonDefaults.HighlightMaskEnd to Color.White,
                DaxSkeletonDefaults.BaseMaskEnd to restingMask,
            ),
            start = gradientStart,
            end = gradientEnd,
        )
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }
}

internal object DaxSkeletonDefaults {
    val LineHeight: Dp = 16.dp
    val CircleSize: Dp = 40.dp
    const val SweepDurationMillis: Int = 1000
    const val SweepDelayMillis: Int = 0
    const val RestingAlpha: Float = 0.3f
    const val RestingProgress: Float = 0.5f
    const val BaseMaskStart: Float = 0.25f
    const val HighlightMaskStart: Float = 0.4995f
    const val HighlightMaskEnd: Float = 0.5005f
    const val BaseMaskEnd: Float = 0.75f
    val SweepTiltRadians: Float = Math.toRadians(20.0).toFloat()

    val color: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.system.lines
}

@PreviewLightDark
@Composable
private fun DaxSkeletonPreview() {
    PreviewBox {
        Column(
            modifier = Modifier.daxSkeletonShimmer(animated = true),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
