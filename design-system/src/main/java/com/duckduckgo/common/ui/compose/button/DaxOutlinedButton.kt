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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo base version of an outlined button.
 *
 * This is *not* part of the design system and should *only* be used when creating a
 * *new* design system compliant button.
 *
 * See [DaxSecondaryButton] and [DaxButtonGhost] for example usage.
 */
@Composable
internal fun DaxOutlinedButton(
    text: String,
    onClick: () -> Unit,
    colors: DaxButtonColors,
    border: BorderStroke,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    enabled: Boolean = true,
) {
    DaxButton(
        text = text,
        onClick = onClick,
        size = size,
        colors = colors,
        modifier = modifier,
        rippleConfiguration = rippleConfiguration,
        border = border,
        enabled = enabled,
    )
}

@Preview
@Composable
private fun DaxOutlinedButtonSmallPreview() {
    PreviewBox {
        DaxOutlinedButton(
            text = "DaxOutlinedButtonSmall",
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            border = BorderStroke(
                width = 1.dp,
                color = DuckDuckGoTheme.colors.accentBlue,
            ),
        )
    }
}

@Preview
@Composable
private fun DaxOutlinedButtonLargePreview() {
    PreviewBox {
        DaxOutlinedButton(
            text = "DaxOutlinedButtonLarge",
            size = DaxButtonSize.Large,
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            border = BorderStroke(
                width = 1.dp,
                color = DuckDuckGoTheme.colors.accentBlue,
            ),
        )
    }
}

@Composable
private fun previewButtonColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Transparent,
    contentColor = Color.Black,
    disabledContainerColor = Color.LightGray,
    disabledContentColor = Color.DarkGray,
)
