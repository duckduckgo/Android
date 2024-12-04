/*
 * Copyright (c) 2024 DuckDuckGo
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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DuckDuckGoColors(
    val background: Color,
    val backgroundInverted: Color,
    val surface: Color,
    val container: Color,
    val window: Color,
    val primaryText: Color,
    val primaryInvertedText: Color,
    val secondaryText: Color,
    val secondaryInvertedText: Color,
    val tertiaryText: Color,
    val primaryIcon: Color,
    val iconDisabled: Color,
    val destructive: Color,
    val lines: Color,
    val accentBlue: Color,
    val accentYellow: Color,
    val containerDisabled: Color,
    val textDisabled: Color,
    val ripple: Color,
    val logoTitleText: Color,
    val omnibarTextColorHighlight: Color,
)

val LocalDuckDuckGoColors = staticCompositionLocalOf<DuckDuckGoColors> {
    error("No DuckDuckGoColors provided")
}

val LightColorPalette = DuckDuckGoColors(
    background = Gray0,
    backgroundInverted = Gray100,
    surface = White,
    container = Black6,
    window = White,
    primaryText = Black84,
    primaryInvertedText = White84,
    secondaryText = Black60,
    secondaryInvertedText = White60,
    tertiaryText = Black48,
    primaryIcon = Black84,
    iconDisabled = Black40,
    destructive = AlertRedOnLightDefault,
    containerDisabled = Black6,
    textDisabled = Black36,
    lines = Black9,
    accentBlue = Blue50,
    accentYellow = Yellow50,
    ripple = Black6,
    logoTitleText = Gray85,
    omnibarTextColorHighlight = Blue5020,
)

val DarkColorPalette = DuckDuckGoColors(
    background = Gray100,
    backgroundInverted = Gray0,
    surface = Gray90,
    container = White12,
    window = Gray85,
    primaryText = White84,
    primaryInvertedText = Black84,
    secondaryText = White60,
    secondaryInvertedText = Black60,
    tertiaryText = White48,
    primaryIcon = White84,
    iconDisabled = White40,
    destructive = AlertRedOnDarkDefault,
    containerDisabled = White18,
    textDisabled = White36,
    lines = White9,
    accentBlue = Blue30,
    accentYellow = Yellow50,
    ripple = White12,
    logoTitleText = White,
    omnibarTextColorHighlight = Blue3020,
)
