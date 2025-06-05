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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
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
    shapes: DuckDuckGoShapes,
    typography: DuckDuckGoTypography = DuckDuckGoTypography,
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
    content: @Composable () -> Unit,
) {
    val lightColorPalette = DuckDuckGoColors(
        background = colorResource(R.color.gray0),
        backgroundInverted = colorResource(R.color.gray100),
        surface = colorResource(R.color.white),
        container = colorResource(R.color.black6),
        containerDisabled = colorResource(R.color.black6),
        window = colorResource(R.color.white),
        destructive = colorResource(R.color.alertRedOnLightDefault),
        lines = colorResource(R.color.black9),
        accentContentPrimary = colorResource(R.color.white),
        accentBlue = colorResource(R.color.blue50),
        accentYellow = colorResource(R.color.yellow50),
        ripple = colorResource(R.color.black6),
        text = DuckDuckGoTextColors(
            primary = colorResource(R.color.white84),
            primaryInverted = colorResource(R.color.black84),
            secondary = colorResource(R.color.white60),
            secondaryInverted = colorResource(R.color.black60),
            tertiary = colorResource(R.color.white48),
            disabled = colorResource(R.color.black36),
            logoTitle = colorResource(R.color.gray85),
            omnibarHighlight = colorResource(R.color.blue50_20),
        ),
        isDark = false,
    )

    val darkColorPalette = DuckDuckGoColors(
        background = colorResource(R.color.gray100),
        backgroundInverted = colorResource(R.color.gray0),
        surface = colorResource(R.color.gray90),
        container = colorResource(R.color.white12),
        containerDisabled = colorResource(R.color.white18),
        window = colorResource(R.color.gray85),
        destructive = colorResource(R.color.alertRedOnDarkDefault),
        lines = colorResource(R.color.white9),
        accentContentPrimary = colorResource(R.color.blue80),
        accentBlue = colorResource(R.color.blue30),
        accentYellow = colorResource(R.color.yellow50),
        ripple = colorResource(R.color.white12),
        text = DuckDuckGoTextColors(
            primary = colorResource(R.color.white84),
            primaryInverted = colorResource(R.color.black84),
            secondary = colorResource(R.color.white60),
            secondaryInverted = colorResource(R.color.black60),
            tertiary = colorResource(R.color.white48),
            disabled = colorResource(R.color.white36),
            logoTitle = colorResource(R.color.white),
            omnibarHighlight = colorResource(R.color.blue30_20),
        ),
        isDark = true,
    )

    val shapes = DuckDuckGoShapes(
        small = RoundedCornerShape(dimensionResource(R.dimen.smallShapeCornerRadius)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.mediumShapeCornerRadius)),
        large = RoundedCornerShape(dimensionResource(R.dimen.largeShapeCornerRadius)),
    )

    val colors = if (isDarkTheme) darkColorPalette else lightColorPalette

    ProvideDuckDuckGoTheme(colors = colors, shapes = shapes) {
        MaterialTheme(
            colorScheme = debugColors(),
            typography = debugTypography(),
            shapes = debugShapes,
            content = content,
        )
    }
}

/**
 * A Material3 [ColorScheme] implementation which sets all colors to [debugColor] to discourage usage of
 * [MaterialTheme.colorScheme] in preference to [DuckDuckGoTheme.colors].
 */
fun debugColors(debugColor: Color = Color.Magenta) = ColorScheme(
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
)

/**
 * A Material3 [Shapes] implementation which sets all shapes to [CutCornerShape] to discourage usage of
 * [MaterialTheme.shapes] in preference to [DuckDuckGoTheme.shapes].
 */
private val debugShapes = Shapes(
    small = CutCornerShape(50),
    medium = CutCornerShape(50),
    large = CutCornerShape(50),
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
