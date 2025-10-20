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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkDefault18
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkTextPressed
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightDefault18
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightTextPressed
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Asana Task: TODO
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=12110-29654&t=QDZRkhFFjlwerPPK-4
 */
@Composable
fun DaxDestructiveGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    enabled: Boolean = true,
) {
    DaxButton(
        text = text,
        onClick = onClick,
        size = size,
        colors = destructiveGhostColors(),
        rippleConfiguration = destructiveGhostButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun destructiveGhostColors(): DaxButtonColors = DaxButtonColors(
    containerColor = adsColorButtonGhostContainer(),
    contentColor = adsColorButtonGhostText(),
    disabledContainerColor = adsColorButtonGhostContainer(),
    disabledContentColor = adsColorButtonGhostDisabled(),
    pressedContentColor = adsColorButtonGhostTextPressed()
)

@Composable
private fun adsColorButtonGhostContainer(): Color = Color.Transparent

@Composable
private fun adsColorButtonGhostContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        AlertRedOnDarkDefault18
    } else {
        AlertRedOnLightDefault18
    }

@Composable
private fun adsColorButtonGhostText(): Color = DuckDuckGoTheme.colors.destructive

@Composable
private fun adsColorButtonGhostTextPressed(): Color =
    if(DuckDuckGoTheme.colors.isDark) {
        AlertRedOnDarkTextPressed
    } else {
        AlertRedOnLightTextPressed
    }

@Composable
private fun adsColorButtonGhostDisabled(): Color = DuckDuckGoTheme.textColors.disabled

@Composable
private fun destructiveGhostButtonRippleConfiguration() =
    RippleConfiguration(color = adsColorButtonGhostContainerPressed())

@PreviewLightDark
@Composable
private fun DaxDestructiveGhostButtonSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxDestructiveGhostButton(
            text = "Destructive Ghost Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxDestructiveGhostButtonLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxDestructiveGhostButton(
            text = "Destructive Ghost Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}
