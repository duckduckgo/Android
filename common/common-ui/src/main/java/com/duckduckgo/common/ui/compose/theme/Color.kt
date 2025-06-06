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
    val buttons: DuckDuckGoButtonColors,
    val fabs: DuckDuckGoFabColors,
    val switch: DuckDuckGoSwitchColors,
    val slider: DuckDuckGoSliderColors,
    val textInput: DuckDuckGoTextInputColors,
    val infoPanel: DuckDuckGoInfoPanelColors,
    val tab: DuckDuckGoTabColors,
)

@Immutable
data class DuckDuckGoButtonColors(
    val primary: ButtonColors,
    val secondary: ButtonColors,
    val destructive: ButtonColors,
    val ghost: ButtonColors,
    val ghostDestructive: ButtonColors,
    val ghostAlt: ButtonColors,
) {

    @Immutable
    data class ButtonColors(
        val containerColor: Color,
        val contentColor: Color,
        val containerPressedColor: Color,
        val contentPressedColor: Color,
    )
}

@Immutable
data class DuckDuckGoFabColors(
    val primary: FabColors,
    val secondary: FabColors,
) {

    @Immutable
    data class FabColors(
        val containerColor: Color,
        val contentColor: Color,
        val containerPressedColor: Color,
    )
}

@Immutable
data class DuckDuckGoSwitchColors(
    val thumbOn: Color,
    val thumbOff: Color,
    val trackOn: Color,
    val trackOff: Color,
    val thumbDisabledOn: Color,
    val thumbDisabledOff: Color,
    val trackDisabledOn: Color,
    val trackDisabledOff: Color,
)

@Immutable
data class DuckDuckGoSliderColors(
    val activeColor: Color,
    val inactiveColor: Color,
)

@Immutable
data class DuckDuckGoTextInputColors(
    val focusedOutline: Color,
    val enabledOutline: Color,
)

@Immutable
data class DuckDuckGoInfoPanelColors(
    val tooltipBackgroundColor: Color,
    val alertBackgroundColor: Color,
)

@Immutable
data class DuckDuckGoTabColors(
    val highlight: Color,
)

val LocalDuckDuckGoColors = staticCompositionLocalOf<DuckDuckGoColors> {
    error("No DuckDuckGoColors provided")
}
