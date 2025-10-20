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
import com.duckduckgo.common.ui.compose.theme.Blue20
import com.duckduckgo.common.ui.compose.theme.Blue30_20
import com.duckduckgo.common.ui.compose.theme.Blue50_12
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
fun DaxButtonGhost(
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
        colors = ghostColors(),
        rippleConfiguration = ghostButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun ghostColors(): DaxButtonColors = DaxButtonColors(
    containerColor = adsColorButtonGhostContainer(),
    contentColor = adsColorButtonGhostText(),
    disabledContainerColor = adsColorButtonGhostContainer(),
    disabledContentColor = adsColorButtonGhostDisabled(),
    pressedContentColor = adsColorButtonGhostTextPressed(),
)

@Composable
private fun adsColorButtonGhostContainer(): Color = Color.Transparent

@Composable
private fun adsColorButtonGhostText(): Color = DuckDuckGoTheme.colors.accentBlue

@Composable
private fun adsColorButtonGhostTextPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue20
    } else {
        Blue70
    }

@Composable
private fun adsColorButtonGhostDisabled(): Color =
    DuckDuckGoTheme.textColors.disabled

@Composable
private fun adsColorButtonDestructivePrimaryContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue30_20
    } else {
        Blue50_12
    }

@Composable
private fun ghostButtonRippleConfiguration() =
    RippleConfiguration(color = adsColorButtonDestructivePrimaryContainerPressed())

@PreviewLightDark
@Composable
private fun DaxButtonGhostPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonGhost(
            text = "Ghost Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonGhostLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonGhost(
            text = "Ghost Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}

