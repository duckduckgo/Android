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

import android.util.TypedValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

@Composable
internal fun AnimatedDaxOnboardingStepIndicator(
    step: DaxOnboardingDialogStep?,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    var previousStep by remember { mutableStateOf<DaxOnboardingDialogStep?>(null) }
    val transition = remember(step, animate) { StepTransition(step = step, previousStep = previousStep, animate = animate) }
    var exited by remember(transition) { mutableStateOf(false) }

    SideEffect { previousStep = step }

    LaunchedEffect(transition) {
        val duration = DaxOnboardingDialogDefaults.StepIndicatorTransitionDuration.inWholeMilliseconds.toInt()
        if (transition.fadeOut) {
            transition.alpha.animateTo(
                0f,
                tween(duration, easing = Easing { x -> ((cos((x + 1) * PI) / 2.0) + 0.5).toFloat() }),
            )
            exited = true
        } else if (transition.animateDots) {
            transition.progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
        }
    }

    if (!exited) {
        transition.displayedStep?.let {
            DaxOnboardingStepIndicator(
                step = it,
                modifier = modifier.graphicsLayer { alpha = transition.alpha.value },
                transition = { transition.progress.value },
            )
        }
    }
}

private class StepTransition(
    step: DaxOnboardingDialogStep?,
    previousStep: DaxOnboardingDialogStep?,
    animate: Boolean,
) {
    val fadeOut = step == null && previousStep != null && animate
    val displayedStep = step ?: previousStep.takeIf { fadeOut }
    val animateDots = step != null && animate && previousStep != null && previousStep != step && step.current != 1
    val progress = Animatable(if (animateDots) 0f else 1f)
    val alpha = Animatable(1f)
}

@Composable
internal fun DaxOnboardingStepIndicator(
    step: DaxOnboardingDialogStep,
    modifier: Modifier = Modifier,
    transition: () -> Float = { 1f },
) {
    val colors = LocalOnboardingColors.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val metrics = LocalContext.current.resources.displayMetrics
    val labelStyle = DaxOnboardingDialogDefaults.StepIndicatorLabelStyle
    // The View sizes this label through TypedValue, which applies non-linear font scaling.
    val labelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, labelStyle.fontSize.value, metrics)
    val style = labelStyle.copy(fontSize = with(density) { labelSize.toSp() })
    val labelWidth = remember(step.labels, style, density.density, density.fontScale, textMeasurer, layoutDirection) {
        with(density) {
            ceil(step.labels.maxOf { textMeasurer.measure(it, style, softWrap = false).size.width }.toFloat()).toDp()
        }
    }
    val shape = RoundedCornerShape(DaxOnboardingDialogDefaults.StepIndicatorCornerRadius)

    Row(
        modifier = modifier
            .testTag("daxOnboardingStepIndicator")
            .clearAndSetSemantics { text = AnnotatedString(step.labels[step.current - 1]) }
            .background(colors.surfaceTertiary, shape)
            .border(DaxOnboardingDialogDefaults.StepIndicatorBorderWidth, colors.accentAltPrimary, shape)
            .padding(
                start = DaxOnboardingDialogDefaults.StepIndicatorStartPadding.floorToPx(density),
                end = DaxOnboardingDialogDefaults.StepIndicatorEndPadding.floorToPx(density),
                top = DaxOnboardingDialogDefaults.StepIndicatorVerticalPadding.floorToPx(density),
                bottom = DaxOnboardingDialogDefaults.StepIndicatorVerticalPadding.floorToPx(density),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaxOnboardingDialogDefaults.StepIndicatorLabelGap),
    ) {
        StepIndicatorDots(step = step, transition = transition)
        DaxText(
            text = step.labels[step.current - 1],
            modifier = Modifier.widthIn(min = labelWidth),
            style = DuckDuckGoTextStyle(style),
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StepIndicatorDots(
    step: DaxOnboardingDialogStep,
    transition: () -> Float,
) {
    val colors = LocalOnboardingColors.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val activeDot = DaxOnboardingDialogDefaults.StepIndicatorActiveDot
    val inactiveDot = DaxOnboardingDialogDefaults.StepIndicatorInactiveDot
    val dotGap = DaxOnboardingDialogDefaults.StepIndicatorDotGap
    val dotsWidth = activeDot + (inactiveDot + dotGap) * (step.labels.size - 1)

    Canvas(Modifier.size(width = dotsWidth.floorToPx(density), height = activeDot.floorToPx(density))) {
        val progress = if (step.current == 1) 1f else transition().coerceIn(0f, 1f)
        val currentIndex = step.current - 1
        val activeDiameter = activeDot.toPx()
        val inactiveDiameter = inactiveDot.toPx()
        var x = 0f
        repeat(step.labels.size) { index ->
            val diameter = when (index) {
                currentIndex -> inactiveDiameter + (activeDiameter - inactiveDiameter) * progress
                currentIndex - 1 -> activeDiameter + (inactiveDiameter - activeDiameter) * progress
                else -> inactiveDiameter
            }
            val color = when {
                index < currentIndex -> colors.accentPrimary
                index == currentIndex -> lerp(colors.accentAltPrimary, colors.accentPrimary, progress)
                else -> colors.accentAltPrimary
            }
            val centerX = x + diameter / 2f
            drawCircle(
                color = color,
                radius = diameter / 2f,
                center = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - centerX else centerX, size.height / 2f),
            )
            x += diameter + dotGap.toPx()
        }
    }
}

private fun Dp.floorToPx(density: Density): Dp = with(density) { toPx().toInt().toDp() }

@PreviewLightDark
@Composable
private fun DaxOnboardingStepIndicatorFirstStepPreview() {
    DuckDuckGoTheme {
        OnboardingTheme {
            DaxOnboardingStepIndicator(DaxOnboardingDialogStep(1, (1..5).map { "$it of 5" }.toImmutableList()))
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOnboardingStepIndicatorThirdStepPreview() {
    DuckDuckGoTheme {
        OnboardingTheme {
            DaxOnboardingStepIndicator(DaxOnboardingDialogStep(3, (1..5).map { "$it of 5" }.toImmutableList()))
        }
    }
}
