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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.duckduckgo.mobile.android.R

object DuckDuckGoTheme {

    val colors: DuckDuckGoColors
        @Composable
        get() = LocalDuckDuckGoColors.current

    val textColors: DuckDuckGoTextColors
        @Composable
        get() = LocalDuckDuckGoColors.current.text

    val shapes
        @Composable
        get() = LocalDuckDuckGoShapes.current

    val typography
        @Composable
        get() = LocalDuckDuckGoTypography.current
}

@Composable
fun ProvideDuckDuckGoTheme(
    colors: DuckDuckGoColors,
    shapes: DuckDuckGoShapes,
    typography: DuckDuckGoTypography = DuckDuckGoTypography(colors.text.primary),
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
        window = colorResource(R.color.white),
        primaryIcon = colorResource(R.color.black84),
        iconDisabled = colorResource(R.color.black40),
        destructive = colorResource(R.color.alertRedOnLightDefault),
        containerDisabled = colorResource(R.color.black6),
        textDisabled = colorResource(R.color.black36),
        lines = colorResource(R.color.black9),
        accentBlue = colorResource(R.color.blue50),
        accentYellow = colorResource(R.color.yellow50),
        ripple = colorResource(R.color.black6),
        logoTitleText = colorResource(R.color.gray85),
        omnibarTextColorHighlight = colorResource(R.color.blue50_20),
        text = DuckDuckGoTextColors(
            primary = colorResource(R.color.black84),
            primaryInverted = colorResource(R.color.white84),
            secondary = colorResource(R.color.black60),
            secondaryInverted = colorResource(R.color.white60),
            tertiaryText = colorResource(R.color.black48),
        )
    )

    val darkColorPalette = DuckDuckGoColors(
        background = colorResource(R.color.gray100),
        backgroundInverted = colorResource(R.color.gray0),
        surface = colorResource(R.color.gray90),
        container = colorResource(R.color.white12),
        window = colorResource(R.color.gray85),
        primaryIcon = colorResource(R.color.white84),
        iconDisabled = colorResource(R.color.white40),
        destructive = colorResource(R.color.alertRedOnDarkDefault),
        containerDisabled = colorResource(R.color.white18),
        textDisabled = colorResource(R.color.white36),
        lines = colorResource(R.color.white9),
        accentBlue = colorResource(R.color.blue30),
        accentYellow = colorResource(R.color.yellow50),
        ripple = colorResource(R.color.white12),
        logoTitleText = colorResource(R.color.white),
        omnibarTextColorHighlight = colorResource(R.color.blue30_20),
        text = DuckDuckGoTextColors(
            primary = colorResource(R.color.white84),
            primaryInverted = colorResource(R.color.black84),
            secondary = colorResource(R.color.white60),
            secondaryInverted = colorResource(R.color.black60),
            tertiaryText = colorResource(R.color.white48),
        )
    )

    val shapes = DuckDuckGoShapes(
        small = RoundedCornerShape(dimensionResource(R.dimen.smallShapeCornerRadius)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.mediumShapeCornerRadius)),
        large = RoundedCornerShape(dimensionResource(R.dimen.largeShapeCornerRadius)),
    )

    val colors = if (isDarkTheme) darkColorPalette else lightColorPalette

    ProvideDuckDuckGoTheme(colors = colors, shapes = shapes, content = content)
}
