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
import com.duckduckgo.common.ui.compose.theme.Black6
import com.duckduckgo.common.ui.compose.theme.Black60
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White12
import com.duckduckgo.common.ui.compose.theme.White84
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Asana Task: TODO
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=3152-4446
 */

@Composable
fun DaxButtonDestructiveGhostSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButton(
        onClick = onClick,
        colors = destructiveGhostSecondaryColors(),
        rippleConfiguration = destructiveGhostButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    ) {
        DaxButtonText(text)
    }
}

@Composable
fun DaxButtonDestructiveGhostSecondaryLarge(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButtonLarge(
        onClick = onClick,
        colors = destructiveGhostSecondaryColors(),
        rippleConfiguration = destructiveGhostButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    ) {
        DaxButtonText(text)
    }
}

@Composable
private fun destructiveGhostSecondaryColors(): DaxButtonColors = DaxButtonColors(
    containerColor = adsColorButtonGhostALTContainer(),
    contentColor = adsColorButtonGhostALTText(),
    disabledContainerColor = adsColorButtonGhostALTContainer(),
    disabledContentColor = adsColorButtonGhostALTDisabled(),
    pressedContentColor = adsColorButtonGhostALTTextPressed(),
)

@Composable
private fun adsColorButtonGhostALTContainer(): Color = Color.Transparent

@Composable
private fun adsColorButtonGhostALTContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        White12
    } else {
        Black6
    }

@Composable
private fun adsColorButtonGhostALTText(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        White84
    } else {
        Black60
    }

@Composable
private fun adsColorButtonGhostALTTextPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        White84
    } else {
        Black60
    }

@Composable
private fun adsColorButtonGhostALTDisabled(): Color =
    DuckDuckGoTheme.textColors.disabled

@Composable
private fun destructiveGhostButtonRippleConfiguration() =
    RippleConfiguration(color = adsColorButtonGhostALTContainerPressed())

@PreviewLightDark
@Composable
private fun DaxButtonDestructiveGhostSecondaryPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonDestructiveGhostSecondary(
            text = "Destructive Ghost Secondary",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonDestructiveGhostSecondaryLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonDestructiveGhostSecondaryLarge(
            text = "Destructive Ghost Secondary Large",
            onClick = { },
            enabled = enabled,
        )
    }
}
