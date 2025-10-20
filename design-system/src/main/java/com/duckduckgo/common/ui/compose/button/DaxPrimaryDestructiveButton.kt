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
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkPressed
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightPressed
import com.duckduckgo.common.ui.compose.theme.Black84
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
fun DaxPrimaryDestructiveButton(
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
        colors = primaryDestructiveButtonColors(),
        rippleConfiguration = primaryDestructiveRippleConfiguration(),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
private fun primaryDestructiveButtonColors(): DaxButtonColors = DaxButtonColors(
    containerColor = adsColorButtonDestructivePrimaryContainer(),
    contentColor = adsColorButtonDestructivePrimaryText(),
    disabledContainerColor = adsColorButtonDestructivePrimaryContainerDisabled(),
    disabledContentColor = adsColorButtonDestructivePrimaryTextDisabled(),
)

@Composable
private fun adsColorButtonDestructivePrimaryContainer(): Color =
    DuckDuckGoTheme.colors.destructive

@Composable
private fun adsColorButtonDestructivePrimaryText(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        Black84
    } else {
        White
    }

@Composable
private fun adsColorButtonDestructivePrimaryContainerDisabled(): Color =
    DuckDuckGoTheme.colors.containerDisabled

@Composable
private fun adsColorButtonDestructivePrimaryTextDisabled(): Color =
    DuckDuckGoTheme.textColors.disabled

@Composable
private fun adsColorButtonDestructivePrimaryContainerPressed(): Color =
    if (DuckDuckGoTheme.colors.isDark) {
        AlertRedOnDarkPressed
    } else {
        AlertRedOnLightPressed
    }

@Composable
private fun primaryDestructiveRippleConfiguration() =
    RippleConfiguration(color = adsColorButtonDestructivePrimaryContainerPressed())

@PreviewLightDark
@Composable
private fun DaxPrimaryDestructiveButtonSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryDestructiveButton(
            text = "Primary Destructive Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryDestructiveLargeButtonPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryDestructiveButton(
            text = "Primary Destructive Large",
            onClick = { },
            enabled = enabled,
        )
    }
}
