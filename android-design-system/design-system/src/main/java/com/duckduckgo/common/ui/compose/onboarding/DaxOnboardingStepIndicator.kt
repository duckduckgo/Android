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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.DuckSansProduct
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.ceil

private val StepIndicatorShape = RoundedCornerShape(64.dp)

private val StepIndicatorLabelStyle = DuckDuckGoTextStyle(
    TextStyle(
        fontFamily = DuckSansProduct,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
)

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
    val style = StepIndicatorLabelStyle.textStyle
    val labelWidth = remember(step.labels, style, density.density, density.fontScale, textMeasurer, layoutDirection) {
        with(density) {
            ceil(step.labels.maxOf { textMeasurer.measure(it, style, softWrap = false).size.width }.toFloat()).toDp()
        }
    }
    val activeDot = DaxOnboardingDialogDefaults.StepIndicatorActiveDot
    val inactiveDot = DaxOnboardingDialogDefaults.StepIndicatorInactiveDot
    val dotGap = DaxOnboardingDialogDefaults.StepIndicatorDotGap
    val dotWidth = activeDot + (inactiveDot + dotGap) * (step.labels.size - 1)

    Row(
        modifier = modifier
            .testTag("daxOnboardingStepIndicator")
            .clearAndSetSemantics { text = AnnotatedString(step.labels[step.current - 1]) }
            .background(colors.surfaceTertiary, StepIndicatorShape)
            .border(1.5.dp, colors.accentAltPrimary, StepIndicatorShape)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaxOnboardingDialogDefaults.StepIndicatorLabelGap),
    ) {
        Canvas(Modifier.size(width = dotWidth, height = activeDot)) {
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
        DaxText(
            text = step.labels[step.current - 1],
            modifier = Modifier.widthIn(min = labelWidth),
            style = StepIndicatorLabelStyle,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

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
