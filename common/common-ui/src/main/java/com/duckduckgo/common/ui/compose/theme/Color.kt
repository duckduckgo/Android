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
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Outline
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoColors.ButtonColors

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
    val buttonColors: ButtonColors,
    val textInputColors: TextInputColors,
    val dividerColors: DividerColors,
    val switchColors: SwitchColors,
) {

    data class ButtonColors(
        val primaryContainer: Color,
        val primaryContainerPressed: Color,
        val primaryContainerDisabled: Color,
        val secondaryContainer: Color,
        val secondaryContainerPressed: Color,
        val ghostAltContainerPressed: Color,
        val destructiveContainer: Color,
        val destructiveContainerPressed: Color,
        val destructiveGhostContainer: Color,
        val destructiveGhostContainerPressed: Color,
        val primaryText: Color,
        val primaryTextDisabled: Color,
        val primaryTextPressed: Color,
        val secondaryText: Color,
        val secondaryTextPressed: Color,
        val destructiveGhostText: Color,
        val ghostAltText: Color,
        val ghostAltTextPressed: Color,
        val destructiveGhostTextPressed: Color,
    )

    data class TextInputColors(
        val focusedOutline: Color,
        val enabledOutline: Color,
    )

    data class DividerColors(
        val colorLines: Color,
    )

    data class SwitchColors(
        val trackOn: Color,
        val trackOff: Color,
        val trackDisabledOn: Color,
        val trackDisabledOff: Color,
        val thumbOn: Color,
        val thumbOff: Color,
        val thumbDisabledOn: Color,
        val thumbDisabledOff: Color,
    )
}

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
    buttonColors = ButtonColors(
        primaryContainer = Blue50,
        primaryContainerPressed = Blue70,
        primaryContainerDisabled = Black6,
        secondaryContainer = Transparent,
        secondaryContainerPressed = Blue50,
        ghostAltContainerPressed = Black6,
        destructiveContainer = AlertRedOnLightDefault,
        destructiveContainerPressed = AlertRedOnLightPressed,
        destructiveGhostContainer = Transparent,
        destructiveGhostContainerPressed = AlertRedOnLightDefault,
        primaryText = White,
        primaryTextPressed = White,
        primaryTextDisabled = Black36,
        secondaryText = Blue50,
        secondaryTextPressed = Blue70,
        destructiveGhostText = AlertRedOnLightDefault,
        ghostAltText = Black60,
        ghostAltTextPressed = Black60,
        destructiveGhostTextPressed = AlertRedOnLightPressed,
    ),
    textInputColors = DuckDuckGoColors.TextInputColors(
        focusedOutline = Blue50,
        enabledOutline = Black30,
    ),
    dividerColors = DuckDuckGoColors.DividerColors(
        colorLines = Black9,
    ),
    switchColors = DuckDuckGoColors.SwitchColors(
        trackOn = Blue50,
        trackOff = Gray60,
        trackDisabledOn = Gray36,
        trackDisabledOff = Gray36,
        thumbOn = White,
        thumbOff = White,
        thumbDisabledOn = White,
        thumbDisabledOff = White,
    ),
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
    buttonColors = ButtonColors(
        primaryContainer = Blue30,
        primaryContainerPressed = Blue50,
        primaryContainerDisabled = White18,
        secondaryContainer = Transparent,
        secondaryContainerPressed = Blue30,
        ghostAltContainerPressed = White12,
        destructiveContainer = AlertRedOnDarkDefault,
        destructiveContainerPressed = AlertRedOnDarkPressed,
        destructiveGhostContainer = Transparent,
        destructiveGhostContainerPressed = AlertRedOnDarkDefault18,
        primaryText = Black84,
        primaryTextPressed = Black84,
        primaryTextDisabled = White36,
        secondaryText = Blue30,
        secondaryTextPressed = Blue20,
        destructiveGhostText = AlertRedOnDarkDefault,
        ghostAltText = White84,
        ghostAltTextPressed = White84,
        destructiveGhostTextPressed = AlertRedOnDarkTextPressed,
    ),
    textInputColors = DuckDuckGoColors.TextInputColors(
        focusedOutline = Blue30,
        enabledOutline = White30,
    ),
    dividerColors = DuckDuckGoColors.DividerColors(
        colorLines = White9,
    ),
    switchColors = DuckDuckGoColors.SwitchColors(
        trackOn = Blue30,
        trackOff = Gray60,
        trackDisabledOn = Gray30,
        trackDisabledOff = Gray30,
        thumbOn = White,
        thumbOff = White,
        thumbDisabledOn = White48,
        thumbDisabledOff = White48,
    ),
)
