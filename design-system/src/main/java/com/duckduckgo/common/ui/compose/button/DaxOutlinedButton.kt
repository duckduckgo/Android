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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.Black36
import com.duckduckgo.common.ui.compose.theme.Black60
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White36
import com.duckduckgo.common.ui.compose.theme.White84
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo base version of an outlined button.
 *
 * This is *not* part of the design system and should *only* be used when creating a
 * *new* design system compliant button.
 *
 * See [DaxButtonSecondary] and [DaxButtonGhost] for example usage.
 */
@Composable
internal fun DaxOutlinedButton(
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    height: Dp = dimensionResource(R.dimen.buttonSmallHeight),
    contentPadding: PaddingValues = PaddingValues(
        horizontal = dimensionResource(R.dimen.buttonSmallSidePadding),
        vertical = dimensionResource(R.dimen.buttonSmallTopPadding),
    ),
    content: @Composable RowScope.() -> Unit,
) {
    DaxButton(
        onClick = onClick,
        modifier = modifier.height(height),
        colors = DaxButtonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.disabledContainerColor,
            disabledContentColor = colors.disabledContentColor,
        ),
        rippleConfiguration = rippleConfiguration,
        border = border,
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun DaxOutlinedButtonLarge(
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    DaxButtonLarge(
        onClick = onClick,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        border = border,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DaxOutlinedButtonPreview() {
    PreviewBox {
        DaxOutlinedButton(
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            border = BorderStroke(
                width = 1.dp,
                color = DuckDuckGoTheme.colors.accentBlue,
            ),
        ) {
            DaxButtonText(text = "DaxOutlinedButton")
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOutlinedButtonLargePreview() {
    PreviewBox {
        DaxOutlinedButtonLarge(
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            border = BorderStroke(
                width = 1.dp,
                color = DuckDuckGoTheme.colors.accentBlue,
            ),
        ) {
            DaxButtonText(text = "DaxOutlinedButtonLarge")
        }
    }
}

@Composable
private fun previewButtonColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Transparent,
    contentColor = if (DuckDuckGoTheme.colors.isDark) {
        White84
    } else {
        Black60
    },
    disabledContainerColor = Color.Transparent,
    disabledContentColor = if (DuckDuckGoTheme.colors.isDark) {
        White36
    } else {
        Black36
    },
)
