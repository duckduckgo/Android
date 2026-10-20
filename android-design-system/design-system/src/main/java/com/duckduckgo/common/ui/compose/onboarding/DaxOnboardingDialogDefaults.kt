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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Colours used by the onboarding bubble, assembled from the onboarding theme overlay. */
@Immutable
internal data class DaxOnboardingDialogColors(
    val surface: Color,
    val stroke: Color,
    val content: Color,
    val shadow: Color,
)

/**
 * Dimensions and timing shared by both onboarding dialog variants.
 *
 * [EntranceDelay] is the production base delay. A caller's `entryDelay` is added to it; it does
 * not replace it. The CTA colours intentionally come from the existing blue Compose button
 * placeholders until a dedicated onboarding brand button is available.
 */
object DaxOnboardingDialogDefaults {
    val BoundsAnimationDuration: Duration = 400.milliseconds
    val EntranceDelay: Duration = 200.milliseconds
    val Elevation: Dp = 7.dp

    val CornerRadius: Dp = 36.dp
    val StrokeWidth: Dp = 1.5.dp
    val TopPadding: Dp = 28.dp
    val BottomPadding: Dp = 20.dp
    val TextHorizontalPadding: Dp = 32.dp
    val ContentHorizontalPadding: Dp = 20.dp
    val HeaderImageSize: Dp = 96.dp
    val HeaderTitleSpacing: Dp = 16.dp
    val TitleBodySpacing: Dp = 16.dp
    internal val ViewTitleLineSpacingExtraMagnitude = 1.sp
    val HeaderContentSpacing: Dp = 20.dp

    // The View CTA's 20dp margin plus the 4dp vertical inset of a small rebrand button.
    val ContentToPrimaryCtaSpacing: Dp = 24.dp
    val CtaSpacing: Dp = 8.dp
    val DismissSize: Dp = 32.dp
    val DismissOffset: Dp = 4.dp
    val TailBandHeight: Dp = DaxSpeechBubbleShape.TailHeight + StrokeWidth

    val StepIndicatorEndInset: Dp = 38.dp
    val StepIndicatorActiveDot: Dp = 12.dp
    val StepIndicatorInactiveDot: Dp = 6.dp
    val StepIndicatorDotGap: Dp = 4.dp
    val StepIndicatorLabelGap: Dp = 20.dp
    val StepIndicatorTransitionDuration: Duration = 300.milliseconds

    internal val ContainerFadeDuration: Duration = 400.milliseconds
    internal val ContentFadeDuration: Duration = 200.milliseconds
    internal val HeaderFadeDuration: Duration = 300.milliseconds
    internal val TailPositionDuration: Duration = 400.milliseconds
    internal val TypingCadence: Duration = 20.milliseconds
    internal val TypingSettle: Duration = 20.milliseconds

    internal val LandscapeCtaWidth = 200.dp
    internal val LandscapeTextCtaSpacing = 8.dp
    internal val LandscapeTitleBodySpacing = 8.dp

    /** Colours for the current [com.duckduckgo.common.ui.compose.theme.OnboardingTheme]. */
    internal val colors: DaxOnboardingDialogColors
        @Composable
        @ReadOnlyComposable
        get() = DaxOnboardingDialogColors(
            surface = LocalOnboardingColors.current.surfaceTertiary,
            stroke = LocalOnboardingColors.current.accentAltPrimary,
            content = LocalOnboardingColors.current.textPrimary,
            shadow = LocalOnboardingColors.current.bubbleShadow,
        )

    /** The text colour used by the dialog's title and body. */
    val ContentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = colors.content

    /** The card surface colour used by the dialog. */
    val SurfaceColor: Color
        @Composable
        @ReadOnlyComposable
        get() = colors.surface

    /** The accent colour used by the card outline. */
    val StrokeColor: Color
        @Composable
        @ReadOnlyComposable
        get() = colors.stroke

    /** The shadow colour used by both supported shadow paths. */
    val ShadowColor: Color
        @Composable
        @ReadOnlyComposable
        get() = colors.shadow
}
