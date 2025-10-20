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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.duckduckgo.common.ui.compose.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.Black12
import com.duckduckgo.common.ui.compose.theme.Blue20
import com.duckduckgo.common.ui.compose.theme.Blue30
import com.duckduckgo.common.ui.compose.theme.Blue30_20
import com.duckduckgo.common.ui.compose.theme.Blue50
import com.duckduckgo.common.ui.compose.theme.Blue50_12
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White24
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Asana Task: TODO
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=12110-29650
 */
@Composable
fun DaxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    enabled: Boolean = true,
) {
    DaxOutlinedButton(
        text = text,
        onClick = onClick,
        size = size,
        colors = secondaryColors(),
        rippleConfiguration = secondaryButtonRippleConfiguration(),
        border = border(enabled = enabled),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun secondaryColors(): DaxButtonColors =
    DaxButtonColors(
        containerColor = adsColorButtonSecondaryContainer(),
        contentColor = adsColorButtonSecondaryText(),
        disabledContainerColor = adsColorButtonSecondaryContainer(),
        disabledContentColor = adsColorButtonSecondaryTextDisabled(),
        pressedContentColor = adsColorButtonSecondaryTextPressed(),
    )

@Composable
private fun border(enabled: Boolean): BorderStroke =
    BorderStroke(
        width = 1.dp,
        color = if (enabled) {
            adsColorButtonSecondaryContainerBorder()
        } else {
            adsColorButtonSecondaryContainerBorderDisabled()
        },
    )

@Composable
private fun adsColorButtonSecondaryContainer(): Color = Color.Transparent

@Composable
private fun adsColorButtonSecondaryContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue30_20
    } else {
        Blue50_12
    }

@Composable
private fun adsColorButtonSecondaryText(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue30
    } else {
        Blue50
    }

@Composable
private fun adsColorButtonSecondaryTextPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue20
    } else {
        Blue70
    }

@Composable
private fun adsColorButtonSecondaryContainerBorder(): Color = DuckDuckGoTheme.colors.accentBlue

@Composable
private fun adsColorButtonSecondaryTextDisabled(): Color = DuckDuckGoTheme.textColors.disabled

@Composable
private fun adsColorButtonSecondaryContainerBorderDisabled(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        White24
    } else {
        Black12
    }

@Composable
private fun secondaryButtonRippleConfiguration() =
    RippleConfiguration(color = adsColorButtonSecondaryContainerPressed())

@PreviewLightDark
@Composable
private fun DaxSecondaryButtonPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxSecondaryButton(
            text = "Secondary Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonSecondaryLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxSecondaryButton(
            text = "Secondary Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}
