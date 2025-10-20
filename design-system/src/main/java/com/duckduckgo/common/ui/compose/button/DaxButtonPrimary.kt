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

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.duckduckgo.common.ui.compose.theme.Black6
import com.duckduckgo.common.ui.compose.theme.Black84
import com.duckduckgo.common.ui.compose.theme.Blue50
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.theme.White6
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
fun DaxButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButton(
        onClick = onClick,
        colors = primaryColors(),
        rippleConfiguration = primaryButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    ) {
        DaxButtonText(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxButtonPrimaryLarge(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButtonLarge(
        onClick = onClick,
        colors = primaryColors(),
        rippleConfiguration = primaryButtonRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    ) {
        DaxButtonText(text)
    }
}

@Composable
private fun primaryColors(): DaxButtonColors = DaxButtonColors(
    containerColor = adsColorButtonPrimaryContainer(),
    contentColor = adsColorButtonPrimaryText(),
    disabledContainerColor = adsColorButtonPrimaryContainerDisabled(),
    disabledContentColor = adsColorButtonPrimaryTextDisabled(),
)

@Composable
private fun adsColorButtonPrimaryContainer(): Color = DuckDuckGoTheme.colors.accentBlue

@Composable
private fun adsColorButtonPrimaryContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Blue50
    } else {
        Blue70
    }

@Composable
private fun adsColorButtonPrimaryText(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Black84
    } else {
        White
    }

@Composable
private fun adsColorButtonPrimaryContainerDisabled(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        White6
    } else {
        Black6
    }

@Composable
private fun adsColorButtonPrimaryTextDisabled(): Color = DuckDuckGoTheme.textColors.disabled

@Composable
private fun primaryButtonRippleConfiguration() =
    RippleConfiguration(
        color = adsColorButtonPrimaryContainerPressed(),
        rippleAlpha = RippleAlpha(
            pressedAlpha = 1f,
            focusedAlpha = 0.24f,
            draggedAlpha = 0.16f,
            hoveredAlpha = 0.08f,
        ),
    )

@PreviewLightDark
@Composable
private fun DaxButtonPrimaryPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonPrimary(
            text = "Primary",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonPrimaryLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxButtonPrimaryLarge(
            text = "Primary Large",
            onClick = { },
            enabled = enabled,
        )
    }
}
