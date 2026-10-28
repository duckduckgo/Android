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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.ProvideDuckDuckGoTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import kotlin.math.roundToInt
import com.duckduckgo.fonts.R as FontsR
import com.duckduckgo.mobile.android.R as CommonR

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
internal fun nativeOnboardingLineHeight(height: TextUnit, fontSize: TextUnit = 16.sp): TextUnit {
    val density = LocalDensity.current
    if (density.fontScale == 1f) return height
    val heightSize = nativeOnboardingTextSize(height)
    val font = nativeOnboardingTextSize(fontSize)
    return with(density) { (heightSize.toPx() / font.toPx()).em }
}

internal val OnboardingBodyFontFamily = FontFamily(Font(FontsR.font.ducksansproduct_regular))

internal val OnboardingBodyParagraphStyle: ParagraphStyle
    @Composable get() = onboardingParagraphStyle(OnboardingBodyFontFamily)

internal val OnboardingLabelParagraphStyle: ParagraphStyle
    @Composable get() = onboardingParagraphStyle(OnboardingLabelSpanStyle.fontFamily!!)

@Composable
private fun onboardingParagraphStyle(fontFamily: FontFamily): ParagraphStyle {
    val density = LocalDensity.current
    val typeface = LocalFontFamilyResolver.current.resolve(fontFamily).value as Typeface
    val fontSize = nativeOnboardingTextSize(16.sp)
    val lineHeight = nativeOnboardingTextSize(20.sp)
    return remember(density, typeface, fontSize, lineHeight) {
        with(density) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize.toPx()
                this.typeface = Typeface.SANS_SERIF
            }
            // DaxTextView sets Body1 line spacing before its XML font resource finishes loading.
            val spacingExtra = lineHeight.roundToPx() - paint.getFontMetricsInt(null)
            paint.typeface = typeface
            ParagraphStyle(
                lineHeight = if (density.fontScale == 1f) {
                    (paint.getFontMetricsInt(null) + spacingExtra).toSp()
                } else {
                    ((paint.getFontMetricsInt(null) + spacingExtra) / fontSize.toPx()).em
                },
                platformStyle = PlatformParagraphStyle(includeFontPadding = true),
            )
        }
    }
}
internal val OnboardingLabelSpanStyle = SpanStyle(fontFamily = FontFamily(Font(FontsR.font.ducksansdisplay_regular)))

internal val ContentConfig.titleHorizontalPadding
    get() = when (this) {
        is ContentConfig.Welcome -> DaxOnboardingDialogDefaults.ContentHorizontalPadding
        is ContentConfig.PreferenceSelector,
        is ContentConfig.SingleChoice,
        is ContentConfig.DuckAiState,
        is ContentConfig.TogglePosition,
        -> DaxOnboardingDialogDefaults.ContentHorizontalPadding + DaxOnboardingDialogDefaults.TextHorizontalPadding
        else -> DaxOnboardingDialogDefaults.TextHorizontalPadding
    }

internal val ContentConfig.contentHorizontalPadding
    get() = if (this is ContentConfig.QuickSetup) 0.dp else DaxOnboardingDialogDefaults.ContentHorizontalPadding

@Composable
internal fun OnboardingDialogContent(state: OnboardingContentState, enabled: Boolean) {
    val colors = DuckDuckGoTheme.colors
    ProvideDuckDuckGoTheme(
        colors = colors.copy(text = colors.text.copy(primary = Color(LocalContext.current.getColorFromAttr(CommonR.attr.onboardingTextPrimary)))),
        typography = DuckDuckGoTheme.typography,
    ) {
        when (val content = state.config) {
            is ContentConfig.Welcome -> WelcomeContent(content)
            is ContentConfig.ComparisonChart -> ComparisonChartContent(content.config, state.readyAnimated, state.skipped)
            is ContentConfig.AddressBar -> AddressBarContent(content, state, enabled)
            is ContentConfig.InputScreen -> InputScreenContent(content, state, enabled)
            is ContentConfig.WidgetPrompt -> WidgetPromptContent(content)
            is ContentConfig.InputScreenPreview -> InputScreenPreviewContent(content, state, enabled)
            is ContentConfig.QuickSetup -> QuickSetupContent(content, state, enabled)
            is ContentConfig.DownloadReason -> DownloadReasonContent(content, state, enabled)
            is ContentConfig.PreferenceSelector -> PreferenceSelectorContent(content, state, enabled)
            is ContentConfig.SingleChoice -> SingleChoiceContent(content, state, enabled)
            is ContentConfig.DuckAiState -> DuckAiStateContent(content, state, enabled)
            is ContentConfig.TogglePosition -> TogglePositionContent(content, state, enabled)
            is ContentConfig.ImportPasswords -> ImportPasswordsContent(content)
            is ContentConfig.ImportComplete -> ImportCompleteContent(content, state)
        }
    }
}
