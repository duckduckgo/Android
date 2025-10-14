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

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.duckduckgo.mobile.android.R

@Immutable
data class DuckDuckGoColors(
    val background: Color,
    val backgroundInverted: Color,
    val surface: Color,
    val container: Color,
    val containerDisabled: Color,
    val window: Color,
    val destructive: Color,
    val lines: Color,
    val accentContentPrimary: Color,
    val accentBlue: Color,
    val accentYellow: Color,
    val ripple: Color,
    val text: DuckDuckGoTextColors,
    val isDark: Boolean, // TODO we'll need to do an exploration into using the app pref for Theme switching
)

@Immutable
data class DuckDuckGoTextColors(
    val primary: Color,
    val primaryInverted: Color,
    val secondary: Color,
    val secondaryInverted: Color,
    val tertiary: Color,
    val disabled: Color,
    val logoTitle: Color,
    val omnibarHighlight: Color,
)

@SuppressLint("ComposeCompositionLocalUsage")
val LocalDuckDuckGoColors = staticCompositionLocalOf<DuckDuckGoColors> {
    error("No DuckDuckGoColors provided")
}

//region Black color variants
val Black84 = Color(0xD6000000)
val Black60 = Color(0x99000000)
val Black50 = Color(0x80000000)
val Black48 = Color(0x7A000000)
val Black40 = Color(0x66000000)
val Black36 = Color(0x5C000000)
val Black30 = Color(0x4D000000)
val Black35 = Color(0x35000000)
val Black18 = Color(0x2E000000)
val Black12 = Color(0x1F000000)
val Black9 = Color(0x17000000)
val Black6 = Color(0x0F000000)
val Black3 = Color(0x08000000)
val Black = Color(0xFF000000)
//endregion

//region White color variants
val White84 = Color(0xD6FFFFFF)
val White60 = Color(0x99FFFFFF)
val White48 = Color(0x7AFFFFFF)
val White40 = Color(0x66FFFFFF)
val White36 = Color(0x5CFFFFFF)
val White30 = Color(0x4DFFFFFF)
val White24 = Color(0x3DFFFFFF)
val White18 = Color(0x2EFFFFFF)
val White12 = Color(0x1FFFFFFF)
val White9 = Color(0x17FFFFFF)
val White6 = Color(0x0FFFFFFF)
val White3 = Color(0x08FFFFFF)
val White = Color(0xFFFFFFFF)
//endregion

// region Blue color variants
val Blue0
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue0)
val Blue0_50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue0_50)
val Blue10
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue10)
val Blue20
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue20)
val Blue30
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue30)
val Blue30_20
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue30_20)
val Blue50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue50)
val Blue50_20
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue50_20)
val Blue50_14
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue50_14)
val Blue50_12
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue50_12)
val Blue60
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue60)
val Blue70
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue70)
val Blue80
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue80)
// endregion

// region Design System Brand Colors
val DisabledColor
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.disabledColor)
val AlertGreen
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertGreen)
val AlertRedOnLightDefault
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnLightDefault)
val AlertRedOnLightDefault18
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnLightDefault_18)
val AlertRedOnLightPressed
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnLightPressed)
val AlertRedOnDarkDefault
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnDarkDefault)
val AlertRedOnDarkDefault18
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnDarkDefault_18)
val AlertRedOnDarkPressed
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnDarkPressed)
val AlertRedOnLightTextPressed
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnLightTextPressed)
val AlertRedOnDarkTextPressed
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.alertRedOnDarkTextPressed)
val DaxColorBlurLight
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.daxColorBlurLight)
val DaxColorBlurDark
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.daxColorBlurDark)
// endregion

// region Red color variants
val Red20
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red20)
val Red30
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red30)
val Red30_18
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red30_18)
val Red50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red50)
val Red60
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red60)
val Red60_12
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red60_12)
val Red70
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.red70)
// endregion

// region Purple color variants
val Purple50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.purple50)
val Purple40
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.purple40)
// endregion

// region Yellow color variants
val Yellow50_14
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.yellow50_14)
val Yellow50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.yellow50)
val Yellow10
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.yellow10)
// endregion

// region Green color variants
val Green0
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.green0)
val Green50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.green50)
val Green70
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.green70)
val Green80
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.green80)
// endregion

// region Gray color variants
val Gray100
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray100)
val Gray95
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray95)
val Gray90
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray90)
val Gray85
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray85)
val Gray80
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray80)
val Gray70
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray70)
val Gray60
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray60)
val Gray60_50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray60_50)
val Gray50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray50)
val Gray40
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray40)
val Gray40_40
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray40_40)
val Gray40_50
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray40_50)
val Gray36
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray36)
val Gray30
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray30)
val Gray25
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray25)
val Gray20
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray20)
val Gray15
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray15)
val Gray0
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.gray0)
// endregion

// region Pink color variants
val Pink100
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.pink100)
val Pink90
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.pink90)
// endregion

// region Brand colors
val Magenta
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.magenta)
val Purple
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.purple)
val Green
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.green)
val Yellow
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.yellow)
val Blue
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.blue)
val Grey
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.grey)
// endregion

// region Special colors
val White_60
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.white_60)
val Transparent
    @Composable
    @ReadOnlyComposable
    get() = colorResource(R.color.transparent)
// endregion
