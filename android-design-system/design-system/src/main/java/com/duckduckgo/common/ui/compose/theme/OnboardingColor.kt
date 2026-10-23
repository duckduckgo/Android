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

package com.duckduckgo.common.ui.compose.theme

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Temporary onboarding-only colour overlay. This is a for-now choice while onboarding Compose
 * theming settles; it intentionally overlays, rather than replacing, [DuckDuckGoTheme].
 */
@Immutable
internal data class OnboardingColors(
    val surfaceTertiary: Color,
    val accentAltPrimary: Color,
    val accentPrimary: Color,
    val textPrimary: Color,
    val iconsPrimary: Color,
    val bubbleShadow: Color,
)

@SuppressLint("ComposeCompositionLocalUsage")
internal val LocalOnboardingColors = compositionLocalOf<OnboardingColors> {
    error("No OnboardingTheme found — wrap the content in OnboardingTheme { }")
}

/**
 * Provides the temporary onboarding colour overlay inside [DuckDuckGoTheme].
 * Call this provider around every onboarding dialog; the dialog reads its colours from the
 * onboarding composition local, and omitting the provider causes that local to throw.
 *
 * @param content Content rendered with the onboarding colour overlay installed.
 */
@Composable
fun OnboardingTheme(content: @Composable () -> Unit) {
    val colors = if (DuckDuckGoTheme.colors.isDark) darkOnboardingColors else lightOnboardingColors()
    val typography = DuckDuckGoTheme.typography.copy(
        h4 = nativeOnboardingTextStyle(DuckDuckGoTheme.typography.h4),
        body1 = nativeOnboardingParagraphScale(DuckDuckGoTheme.typography.body1),
        body2 = nativeOnboardingParagraphScale(DuckDuckGoTheme.typography.body2),
        onboardingBodyInContext = nativeOnboardingParagraphScale(DuckDuckGoTheme.typography.onboardingBodyInContext),
        onboardingTitle = nativeOnboardingTextStyle(DuckDuckGoTheme.typography.onboardingTitle, lineSpacingExtra = (-1).sp),
        onboardingTitleInContext = nativeOnboardingTextStyle(DuckDuckGoTheme.typography.onboardingTitleInContext, lineSpacingExtra = (-1).sp),
        onboardingBody = nativeOnboardingTextStyle(DuckDuckGoTheme.typography.onboardingBody),
        onboardingButton = nativeOnboardingTextStyle(DuckDuckGoTheme.typography.onboardingButton),
    )

    CompositionLocalProvider(
        LocalOnboardingColors provides colors,
        LocalDuckDuckGoTypography provides typography,
        content = content,
    )
}

@Composable
private fun nativeOnboardingParagraphScale(style: DuckDuckGoTextStyle): DuckDuckGoTextStyle {
    val density = LocalDensity.current
    if (density.fontScale == 1f) return style
    val textStyle = style.asTextStyle
    val fontSize = nativeOnboardingTextSize(textStyle.fontSize)
    val lineHeight = nativeOnboardingTextSize(textStyle.lineHeight)
    return DuckDuckGoTextStyle(
        textStyle.copy(
            fontSize = fontSize,
            lineHeight = with(density) { (lineHeight.toPx() / fontSize.toPx()).em },
        ),
    )
}

@Composable
internal fun nativeOnboardingTextSize(size: TextUnit): TextUnit {
    check(size.isSp) { "Only Sp can convert to Px" }
    val density = LocalDensity.current
    if (density.fontScale == 1f) return size
    val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.value, LocalContext.current.resources.displayMetrics)
    val roundedPixels = when {
        pixels < 0f -> -(-pixels).roundToInt().coerceAtLeast(1)
        pixels > 0f -> pixels.roundToInt().coerceAtLeast(1)
        else -> 0
    }
    return with(density) { roundedPixels.toSp() }
}

@Composable
private fun nativeOnboardingTextStyle(style: DuckDuckGoTextStyle, lineSpacingExtra: TextUnit = 0.sp): DuckDuckGoTextStyle {
    val density = LocalDensity.current
    val textStyle = style.asTextStyle
    val fontSize = nativeOnboardingTextSize(textStyle.fontSize)
    val lineSpacing = nativeOnboardingTextSize(lineSpacingExtra)
    val typeface = LocalFontFamilyResolver.current.resolve(
        fontFamily = textStyle.fontFamily,
        fontWeight = textStyle.fontWeight ?: FontWeight.Normal,
        fontStyle = textStyle.fontStyle ?: FontStyle.Normal,
        fontSynthesis = textStyle.fontSynthesis ?: FontSynthesis.All,
    ).value as Typeface
    return remember(style, fontSize, lineSpacing, density, typeface) {
        with(density) {
            val fontSizePx = fontSize.roundToPx()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSizePx.toFloat()
                this.typeface = typeface
            }
            val spacing = lineSpacing.toPx()
            val spacingPx = when {
                spacing < 0f -> -(-spacing).roundToInt().coerceAtLeast(1)
                spacing > 0f -> spacing.roundToInt().coerceAtLeast(1)
                else -> 0
            }
            DuckDuckGoTextStyle(
                textStyle.copy(
                    fontSize = fontSizePx.toSp(),
                    lineHeight = if (density.fontScale == 1f) {
                        (paint.getFontMetricsInt(null) + spacingPx).toSp()
                    } else {
                        ((paint.getFontMetricsInt(null) + spacingPx) / fontSizePx.toFloat()).em
                    },
                    letterSpacing = 0.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.Both),
                    textMotion = TextMotion.Static,
                ),
            )
        }
    }
}

private val darkOnboardingColors = OnboardingColors(
    surfaceTertiary = Color(0xFF011D34),
    accentAltPrimary = Color(0xFF133E7C),
    accentPrimary = Color(0xFF75B6EB),
    textPrimary = White,
    iconsPrimary = Color(0xD6FBFAF9),
    bubbleShadow = Color(0x08000000),
)

@Composable
private fun lightOnboardingColors() = OnboardingColors(
    surfaceTertiary = White,
    accentAltPrimary = Pondwater20,
    accentPrimary = Pondwater50,
    textPrimary = Color(0xF5242323),
    iconsPrimary = Color(0xD6242323),
    bubbleShadow = Color(0x08000000),
)
