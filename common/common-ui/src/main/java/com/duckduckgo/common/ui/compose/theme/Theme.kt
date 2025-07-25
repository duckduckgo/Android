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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoButtonColors.ButtonColors
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoFabColors.FabColors
import com.duckduckgo.mobile.android.R

object DuckDuckGoTheme {

    val colors: DuckDuckGoColors
        @Composable
        get() = LocalDuckDuckGoColors.current

    val buttons: DuckDuckGoButtonColors
        @Composable
        get() = LocalDuckDuckGoColors.current.buttons

    val fabs: DuckDuckGoFabColors
        @Composable
        get() = LocalDuckDuckGoColors.current.fabs

    val switch: DuckDuckGoSwitchColors
        @Composable
        get() = LocalDuckDuckGoColors.current.switch

    val slider: DuckDuckGoSliderColors
        @Composable
        get() = LocalDuckDuckGoColors.current.slider

    val textInput: DuckDuckGoTextInputColors
        @Composable
        get() = LocalDuckDuckGoColors.current.textInput

    val infoPanel: DuckDuckGoInfoPanelColors
        @Composable
        get() = LocalDuckDuckGoColors.current.infoPanel

    val tab: DuckDuckGoTabColors
        @Composable
        get() = LocalDuckDuckGoColors.current.tab

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
    typography: DuckDuckGoTypography = DuckDuckGoTypography(colors.primaryText),
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
    isDesignExperimentEnabled: Boolean = false,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val lightColorPalette = DuckDuckGoColors(
        background = colorResource(R.color.gray0),
        backgroundInverted = colorResource(R.color.gray100),
        surface = colorResource(R.color.white),
        container = colorResource(R.color.black6),
        window = colorResource(R.color.white),
        primaryText = colorResource(R.color.black84),
        primaryInvertedText = colorResource(R.color.white84),
        secondaryText = colorResource(R.color.black60),
        secondaryInvertedText = colorResource(R.color.white60),
        tertiaryText = colorResource(R.color.black48),
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
        buttons = DuckDuckGoButtonColors(
            primary = ButtonColors(
                containerColor = colorResource(R.color.blue50),
                contentColor = colorResource(R.color.white),
                containerPressedColor = colorResource(R.color.blue70),
                contentPressedColor = colorResource(R.color.white),
            ),
            secondary = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.blue50),
                containerPressedColor = colorResource(R.color.blue50),
                contentPressedColor = colorResource(R.color.blue70),
            ),
            destructive = ButtonColors(
                containerColor = colorResource(R.color.alertRedOnLightDefault),
                contentColor = colorResource(R.color.white),
                containerPressedColor = colorResource(R.color.alertRedOnLightPressed),
                contentPressedColor = colorResource(R.color.white),
            ),
            ghost = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.blue50),
                containerPressedColor = colorResource(R.color.black6),
                contentPressedColor = colorResource(R.color.blue50),
            ),
            ghostDestructive = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.alertRedOnLightDefault),
                containerPressedColor = colorResource(R.color.alertRedOnLightDefault),
                contentPressedColor = colorResource(R.color.alertRedOnLightPressed),
            ),
            ghostAlt = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.black60),
                containerPressedColor = colorResource(R.color.black6),
                contentPressedColor = colorResource(R.color.black60),
            ),
        ),
        fabs = DuckDuckGoFabColors(
            primary = FabColors(
                containerColor = colorResource(R.color.blue50),
                contentColor = colorResource(R.color.white),
                containerPressedColor = colorResource(R.color.blue70),
            ),
            secondary = FabColors(
                containerColor = colorResource(R.color.blue0),
                contentColor = colorResource(R.color.blue70),
                containerPressedColor = colorResource(R.color.blue20),
            ),
        ),
        switch = DuckDuckGoSwitchColors(
            thumbOn = colorResource(R.color.white),
            thumbOff = colorResource(R.color.white),
            trackOn = colorResource(R.color.blue50),
            trackOff = colorResource(R.color.gray60_50),
            thumbDisabledOn = colorResource(R.color.white),
            thumbDisabledOff = colorResource(R.color.white),
            trackDisabledOn = colorResource(R.color.gray36),
            trackDisabledOff = colorResource(R.color.gray36),
        ),
        slider = DuckDuckGoSliderColors(
            activeColor = colorResource(R.color.blue50),
            inactiveColor = colorResource(R.color.gray60_50),
        ),
        textInput = DuckDuckGoTextInputColors(
            focusedOutline = colorResource(R.color.blue50),
            enabledOutline = colorResource(R.color.black30),
        ),
        infoPanel = DuckDuckGoInfoPanelColors(
            tooltipBackgroundColor = colorResource(R.color.daxInfoPanelTooltipBackgroundColorLight),
            alertBackgroundColor = colorResource(R.color.daxInfoPanelAlertBackgroundColorLight),
        ),
        tab = DuckDuckGoTabColors(
            highlight = colorResource(R.color.gray60),
        ),
    )

    val darkColorPalette = DuckDuckGoColors(
        background = colorResource(R.color.gray100),
        backgroundInverted = colorResource(R.color.gray0),
        surface = colorResource(R.color.gray90),
        container = colorResource(R.color.white12),
        window = colorResource(R.color.gray85),
        primaryText = colorResource(R.color.white84),
        primaryInvertedText = colorResource(R.color.black84),
        secondaryText = colorResource(R.color.white60),
        secondaryInvertedText = colorResource(R.color.black60),
        tertiaryText = colorResource(R.color.white48),
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
        buttons = DuckDuckGoButtonColors(
            primary = ButtonColors(
                containerColor = colorResource(R.color.blue30),
                contentColor = colorResource(R.color.black84),
                containerPressedColor = colorResource(R.color.blue50),
                contentPressedColor = colorResource(R.color.black84),
            ),
            secondary = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.blue30),
                containerPressedColor = colorResource(R.color.blue30),
                contentPressedColor = colorResource(R.color.blue20),
            ),
            destructive = ButtonColors(
                containerColor = colorResource(R.color.alertRedOnDarkDefault),
                contentColor = colorResource(R.color.black84),
                containerPressedColor = colorResource(R.color.alertRedOnDarkPressed),
                contentPressedColor = colorResource(R.color.black84),
            ),
            ghost = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.blue30),
                containerPressedColor = colorResource(R.color.white12),
                contentPressedColor = colorResource(R.color.blue30),
            ),
            ghostDestructive = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.alertRedOnDarkDefault),
                containerPressedColor = colorResource(R.color.alertRedOnDarkDefault_18),
                contentPressedColor = colorResource(R.color.alertRedOnDarkTextPressed),
            ),
            ghostAlt = ButtonColors(
                containerColor = Color.Transparent,
                contentColor = colorResource(R.color.white84),
                containerPressedColor = colorResource(R.color.white12),
                contentPressedColor = colorResource(R.color.white84),
            ),
        ),
        fabs = DuckDuckGoFabColors(
            primary = FabColors(
                containerColor = colorResource(R.color.blue20),
                contentColor = colorResource(R.color.black),
                containerPressedColor = colorResource(R.color.blue50),
            ),
            secondary = FabColors(
                containerColor = colorResource(R.color.blue0),
                contentColor = colorResource(R.color.blue70),
                containerPressedColor = colorResource(R.color.blue20),
            ),
        ),
        switch = DuckDuckGoSwitchColors(
            thumbOn = colorResource(R.color.white),
            thumbOff = colorResource(R.color.white),
            trackOn = colorResource(R.color.blue30),
            trackOff = colorResource(R.color.gray60_50),
            thumbDisabledOn = colorResource(R.color.white48),
            thumbDisabledOff = colorResource(R.color.white48),
            trackDisabledOn = colorResource(R.color.gray30),
            trackDisabledOff = colorResource(R.color.gray30),
        ),
        slider = DuckDuckGoSliderColors(
            activeColor = colorResource(R.color.blue30),
            inactiveColor = colorResource(R.color.gray40_50),
        ),
        textInput = DuckDuckGoTextInputColors(
            focusedOutline = colorResource(R.color.blue30),
            enabledOutline = colorResource(R.color.white30),
        ),
        infoPanel = DuckDuckGoInfoPanelColors(
            tooltipBackgroundColor = colorResource(R.color.daxInfoPanelTooltipBackgroundColorDark),
            alertBackgroundColor = colorResource(R.color.daxInfoPanelAlertBackgroundColorDark),
        ),
        tab = DuckDuckGoTabColors(
            highlight = colorResource(R.color.gray50),
        ),
    )

    // example of using a different color palette for design experiments
    val designExperimentThemeLight = lightColorPalette.copy(
        background = colorResource(R.color.background_background_light),
        surface = colorResource(R.color.background_surface_light),
        container = colorResource(R.color.background_container_light),
        lines = colorResource(R.color.lines_light),
        primaryText = colorResource(R.color.text_primary_light),
        secondaryText = colorResource(R.color.text_secondary_light),
        primaryIcon = colorResource(R.color.icon_primary_light), // TODO why does this seem to be lighter than in Figma when applied?
    )

    val designExperimentThemeDark = darkColorPalette.copy(
        background = colorResource(R.color.background_background_dark),
        surface = colorResource(R.color.background_surface_dark),
        container = colorResource(R.color.background_container_dark),
        lines = colorResource(R.color.lines_dark),
        primaryText = colorResource(R.color.text_primary_dark),
        secondaryText = colorResource(R.color.text_secondary_dark),
        primaryIcon = colorResource(R.color.icon_primary_dark),
    )

    val shapes = DuckDuckGoShapes(
        small = RoundedCornerShape(dimensionResource(R.dimen.smallShapeCornerRadius)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.mediumShapeCornerRadius)),
        large = RoundedCornerShape(dimensionResource(R.dimen.largeShapeCornerRadius)),
    )

    val colors = when {
        isDesignExperimentEnabled && isDarkTheme -> designExperimentThemeDark
        isDesignExperimentEnabled && !isDarkTheme -> designExperimentThemeLight
        isDarkTheme -> darkColorPalette
        else -> lightColorPalette
    }

    ProvideDuckDuckGoTheme(colors = colors, shapes = shapes, content = content)
}
