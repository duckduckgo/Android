/*
 * Copyright (c) 2025 DuckDuckGo
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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import com.duckduckgo.mobile.android.R

object DuckDuckGoTheme {

    val colors: DuckDuckGoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDuckDuckGoColors.current

    val textColors: DuckDuckGoTextColors
        @Composable
        @ReadOnlyComposable
        get() = colors.text

    val iconColors: DuckDuckGoIconsColors
        @Composable
        @ReadOnlyComposable
        get() = colors.icons

    val shapes
        @Composable
        @ReadOnlyComposable
        get() = LocalDuckDuckGoShapes.current

    val typography
        @Composable
        @ReadOnlyComposable
        get() = LocalDuckDuckGoTypography.current
}

@Composable
fun ProvideDuckDuckGoTheme(
    colors: DuckDuckGoColors,
    shapes: DuckDuckGoShapes = Shapes,
    typography: DuckDuckGoTypography = Typography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDuckDuckGoColors provides colors,
        LocalDuckDuckGoShapes provides shapes,
        LocalDuckDuckGoTypography provides typography,
        content = content,
    )
}

@Composable
fun DuckDuckGoTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    variant: DuckDuckGoThemeVariant = DuckDuckGoThemeVariant.Default,
    content: @Composable () -> Unit,
) {
    val colors = when (variant) {
        DuckDuckGoThemeVariant.Default -> if (isDarkTheme) defaultDarkColors() else defaultLightColors()
        DuckDuckGoThemeVariant.Onboarding -> if (isDarkTheme) onboardingDarkColors() else onboardingLightColors()
    }
    val typography = when (variant) {
        DuckDuckGoThemeVariant.Default -> Typography
        DuckDuckGoThemeVariant.Onboarding -> OnboardingTypography
    }

    ProvideDuckDuckGoTheme(colors = colors, typography = typography) {
        MaterialTheme(
            colorScheme = debugColors(),
            typography = debugTypography(),
            shapes = debugShapes,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
internal fun defaultLightColors(): DuckDuckGoColors = DuckDuckGoColors(
    backgrounds = DuckDuckGoBackgroundColors(
        background = colorResource(R.color.background_background_light),
        backgroundInverted = colorResource(R.color.gray100),
        surface = colorResource(R.color.background_surface_light),
        surfaceTransparent = White30,
        window = colorResource(R.color.background_window_light),
        container = Black6,
        containerDisabled = colorResource(R.color.black6),
    ),
    text = DuckDuckGoTextColors(
        primary = colorResource(R.color.text_primary_light),
        primaryInverted = White84,
        secondary = colorResource(R.color.text_secondary_light),
        secondaryInverted = White60,
        tertiary = Black36,
        destructive = AlertRedOnLightDefault,
        disabled = Black40,
        logoTitle = Gray85,
        omnibarHighlight = colorResource(R.color.blue50_20),
    ),
    brand = DuckDuckGoBrandColors(
        accentBlue = Blue50,
        accentYellow = Yellow50,
        accentBrand50 = Blue50.copy(alpha = .5f),
        accentBrand20 = Blue50_20,
    ),
    icons = DuckDuckGoIconsColors(
        primary = colorResource(R.color.icon_primary_light),
        secondary = colorResource(R.color.icon_secondary_light),
        white = White,
        destructive = AlertRedOnLightDefault,
        text = White,
        disabled = colorResource(R.color.icon_tertiary_light),
    ),
    infoPanel = DuckDuckGoInfoPanelColors(
        backgroundBlue = Blue0_50,
        backgroundYellow = Yellow10,
    ),
    textField = DuckDuckGoTextFieldColors(
        borders = colorResource(R.color.black30),
    ),
    status = DuckDuckGoStatusColors(
        criticalPrimary = colorResource(R.color.alertRedOnLightDefault),
        indicatorActive = AlertGreen,
        indicatorInactive = Gray50,
    ),
    system = DuckDuckGoSystemColors(
        lines = colorResource(R.color.lines_light),
        switchTrackOn = Blue50,
        switchTrackOff = Gray60_50,
        switchThumb = White,
        checkboxOn = Blue50,
        checkboxOff = Blue50,
        checkboxMark = White,
        sliderTrackInactive = Gray60_50,
        textInputEnabledOutline = Black30,
        touchFeedback = colorResource(R.color.controls_fill_primary_light),
        scrim = Scrim,
    ),
    button = DuckDuckGoButtonColors(
        primaryContainer = Blue50,
        primaryContainerPressed = Blue70,
        primaryText = White,
        primaryContainerDisabled = Black6,
        secondaryContainerPressed = Blue50_12,
        secondaryText = Blue50,
        secondaryTextPressed = Blue70,
        secondaryBorderDisabled = Black12,
        ghostAltContainerPressed = Black6,
        ghostAltText = Black60,
        destructiveContainerPressed = AlertRedOnLightPressed,
        destructiveGhostContainerPressed = AlertRedOnLightDefault18,
        destructiveGhostTextPressed = AlertRedOnLightTextPressed,
    ),
    isDark = false,
)

@Composable
@ReadOnlyComposable
internal fun defaultDarkColors(): DuckDuckGoColors = DuckDuckGoColors(
    backgrounds = DuckDuckGoBackgroundColors(
        background = colorResource(R.color.background_background_dark),
        backgroundInverted = colorResource(R.color.gray0),
        surface = colorResource(R.color.background_surface_dark),
        surfaceTransparent = Gray90.copy(alpha = .3f),
        window = colorResource(R.color.background_window_dark),
        container = White12,
        containerDisabled = colorResource(R.color.white18),
    ),
    text = DuckDuckGoTextColors(
        primary = colorResource(R.color.text_primary_dark),
        primaryInverted = colorResource(R.color.black84),
        secondary = colorResource(R.color.text_secondary_dark),
        secondaryInverted = colorResource(R.color.black60),
        tertiary = White36,
        destructive = AlertRedOnDarkDefault,
        disabled = White40,
        logoTitle = White,
        omnibarHighlight = colorResource(R.color.blue30_20),
    ),
    brand = DuckDuckGoBrandColors(
        accentBlue = Blue30,
        accentYellow = Yellow50,
        accentBrand50 = Blue30.copy(alpha = .5f),
        accentBrand20 = Blue30_20,
    ),
    icons = DuckDuckGoIconsColors(
        primary = colorResource(R.color.icon_primary_dark),
        secondary = colorResource(R.color.icon_secondary_dark),
        white = White,
        destructive = AlertRedOnDarkDefault,
        text = Black,
        disabled = colorResource(R.color.icon_tertiary_dark),
    ),
    infoPanel = DuckDuckGoInfoPanelColors(
        backgroundBlue = Blue50_12,
        backgroundYellow = Yellow50_14,
    ),
    textField = DuckDuckGoTextFieldColors(
        borders = colorResource(R.color.white30),
    ),
    status = DuckDuckGoStatusColors(
        criticalPrimary = colorResource(R.color.alertRedOnDarkDefault),
        indicatorActive = AlertGreen,
        indicatorInactive = Gray50,
    ),
    system = DuckDuckGoSystemColors(
        lines = colorResource(R.color.lines_dark),
        switchTrackOff = Gray40_50,
        switchTrackOn = Blue30,
        switchThumb = White,
        checkboxOn = Blue30,
        checkboxOff = Blue30,
        checkboxMark = colorResource(R.color.background_background_dark),
        sliderTrackInactive = Gray40_50,
        textInputEnabledOutline = White30,
        touchFeedback = colorResource(R.color.controls_fill_primary_dark),
        scrim = Scrim,
    ),
    button = DuckDuckGoButtonColors(
        primaryContainer = Blue30,
        primaryContainerPressed = Blue50,
        primaryText = Black84,
        primaryContainerDisabled = White6,
        secondaryContainerPressed = Blue30_20,
        secondaryText = Blue30,
        secondaryTextPressed = Blue20,
        secondaryBorderDisabled = White24,
        ghostAltContainerPressed = White12,
        ghostAltText = White84,
        destructiveContainerPressed = AlertRedOnDarkPressed,
        destructiveGhostContainerPressed = AlertRedOnDarkDefault18,
        destructiveGhostTextPressed = AlertRedOnDarkTextPressed,
    ),
    isDark = true,
)

@Composable
@ReadOnlyComposable
internal fun onboardingLightColors(): DuckDuckGoColors {
    val base = defaultLightColors()
    val onboardingTextPrimary = Color(0xF5242323)
    return base.copy(
        backgrounds = base.backgrounds.copy(
            background = White,
            container = Color(0x17242323),
        ),
        text = base.text.copy(
            primary = onboardingTextPrimary,
            secondary = Black60,
        ),
        brand = base.brand.copy(
            accentBlue = colorResource(R.color.pondwater50),
        ),
        icons = base.icons.copy(
            primary = Color(0xD6242323),
            secondary = Color(0x99383838),
        ),
        button = base.button.copy(
            primaryContainer = colorResource(R.color.mandarin50),
            primaryContainerPressed = colorResource(R.color.mandarin60),
            primaryText = White,
            secondaryContainerPressed = Black12,
            secondaryText = onboardingTextPrimary,
            secondaryTextPressed = onboardingTextPrimary,
        ),
    )
}

@Composable
@ReadOnlyComposable
internal fun onboardingDarkColors(): DuckDuckGoColors {
    val base = defaultDarkColors()
    return base.copy(
        backgrounds = base.backgrounds.copy(
            background = Color(0xFF133E7C),
            container = White6,
        ),
        text = base.text.copy(
            primary = White,
            secondary = White60,
        ),
        brand = base.brand.copy(
            accentBlue = colorResource(R.color.pondwater40),
        ),
        icons = base.icons.copy(
            primary = Color(0xD6FBFAF9),
            secondary = Color(0x7AFBFAF9),
        ),
        button = base.button.copy(
            primaryContainer = colorResource(R.color.pollen30),
            primaryContainerPressed = colorResource(R.color.pollen40),
            primaryText = colorResource(R.color.pollen100),
            secondaryContainerPressed = White24,
            secondaryText = White,
            secondaryTextPressed = White,
        ),
    )
}

/**
 * A Material3 [ColorScheme] implementation which sets all colors to [debugColor] to discourage usage of
 * [MaterialTheme.colorScheme] in preference to [DuckDuckGoTheme.colors].
 */
private fun debugColors(debugColor: Color = Color.Magenta) = ColorScheme(
    primary = debugColor,
    onPrimary = debugColor,
    primaryContainer = debugColor,
    onPrimaryContainer = debugColor,
    inversePrimary = debugColor,
    secondary = debugColor,
    onSecondary = debugColor,
    secondaryContainer = debugColor,
    onSecondaryContainer = debugColor,
    tertiary = debugColor,
    onTertiary = debugColor,
    tertiaryContainer = debugColor,
    onTertiaryContainer = debugColor,
    background = debugColor,
    onBackground = debugColor,
    surface = debugColor,
    onSurface = debugColor,
    surfaceVariant = debugColor,
    onSurfaceVariant = debugColor,
    surfaceTint = debugColor,
    inverseSurface = debugColor,
    inverseOnSurface = debugColor,
    error = debugColor,
    onError = debugColor,
    errorContainer = debugColor,
    onErrorContainer = debugColor,
    outline = debugColor,
    outlineVariant = debugColor,
    scrim = debugColor,
    surfaceBright = debugColor,
    surfaceDim = debugColor,
    surfaceContainer = debugColor,
    surfaceContainerHigh = debugColor,
    surfaceContainerHighest = debugColor,
    surfaceContainerLow = debugColor,
    surfaceContainerLowest = debugColor,
    primaryFixed = debugColor,
    primaryFixedDim = debugColor,
    onPrimaryFixed = debugColor,
    onPrimaryFixedVariant = debugColor,
    secondaryFixed = debugColor,
    secondaryFixedDim = debugColor,
    onSecondaryFixed = debugColor,
    onSecondaryFixedVariant = debugColor,
    tertiaryFixed = debugColor,
    tertiaryFixedDim = debugColor,
    onTertiaryFixed = debugColor,
    onTertiaryFixedVariant = debugColor,
)

/**
 * A Material3 [Shapes] implementation which sets all shapes to [CutCornerShape] to discourage usage of
 * [MaterialTheme.shapes] in preference to [DuckDuckGoTheme.shapes].
 */
private val debugShapes = Shapes(
    extraSmall = CutCornerShape(50),
    small = CutCornerShape(50),
    medium = CutCornerShape(50),
    large = CutCornerShape(50),
    extraLarge = CutCornerShape(50),
)

/**
 * A Material3 [Typography] implementation which sets all text to [debugTextColor] and Cursive to discourage usage of
 * [MaterialTheme.typography] in preference to [DuckDuckGoTheme.typography].
 */
private fun debugTypography(debugTextColor: Color = Color.Magenta) = androidx.compose.material3.Typography().copy(
    displayLarge = androidx.compose.material3.Typography().displayLarge.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    displayMedium = androidx.compose.material3.Typography().displayMedium.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    displaySmall = androidx.compose.material3.Typography().displaySmall.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    titleLarge = androidx.compose.material3.Typography().titleLarge.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    titleMedium = androidx.compose.material3.Typography().titleMedium.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    titleSmall = androidx.compose.material3.Typography().titleSmall.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    bodySmall = androidx.compose.material3.Typography().bodySmall.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    labelLarge = androidx.compose.material3.Typography().labelLarge.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    labelMedium = androidx.compose.material3.Typography().labelMedium.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
    labelSmall = androidx.compose.material3.Typography().labelSmall.copy(
        color = debugTextColor,
        fontFamily = FontFamily.Cursive,
    ),
)
