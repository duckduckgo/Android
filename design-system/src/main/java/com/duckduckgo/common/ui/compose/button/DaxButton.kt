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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo base version of an outlined button.
 *
 * This is *not* part of the design system and should *only* be used when creating a
 * *new* design system compliant button.
 *
 * See [DaxSecondaryButton] and [DaxButtonGhost] for example usage.
 */
@Composable
internal fun DaxButton(
    onClick: () -> Unit,
    size: DaxButtonSize,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val dimensions = resolveButtonDimensions(size = size)

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = dimensions.height),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
            ),
            border = border,
            enabled = enabled,
            shape = DuckDuckGoTheme.shapes.medium,
            contentPadding = dimensions.contentPadding,
            content = {
                content()
            },
        )
    }
}

@Composable
internal fun DaxButton(
    text: String,
    onClick: () -> Unit,
    size: DaxButtonSize,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
) {
    DaxButton(
        onClick = onClick,
        size = size,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        modifier = modifier,
        border = border,
        enabled = enabled,
    ) {
        DaxButtonText(
            text = text,
            color = if (enabled) colors.contentColor else colors.disabledContentColor,
        )
    }
}

@Composable
private fun resolveButtonDimensions(size: DaxButtonSize): DaxButtonDimensions {
    return when (size) {
        DaxButtonSize.Small -> DaxButtonDimensions(
            height = dimensionResource(R.dimen.buttonSmallHeight),
            contentPadding = PaddingValues(
                horizontal = dimensionResource(R.dimen.buttonSmallSidePadding),
                vertical = dimensionResource(R.dimen.buttonSmallTopPadding),
            ),
        )

        DaxButtonSize.Large -> DaxButtonDimensions(
            height = dimensionResource(R.dimen.buttonLargeHeight),
            contentPadding = PaddingValues(
                horizontal = dimensionResource(R.dimen.buttonLargeSidePadding),
                vertical = dimensionResource(R.dimen.buttonLargeTopPadding),
            ),
        )
    }
}

@Composable
private fun DaxButtonText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    DaxText(
        text = text,
        style = DuckDuckGoTheme.typography.button,
        color = color,
        modifier = modifier,
    )
}

@Immutable
data class DaxButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val pressedContentColor: Color = contentColor,
)

@Stable
enum class DaxButtonSize {
    Small,
    Large
}

@Immutable
private data class DaxButtonDimensions(
    val height: Dp,
    val contentPadding: PaddingValues,
)

class DaxButtonStateParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(
        true,
        false,
    )
}

@Composable
@Preview
private fun DaxButtonSmallPreview() {
    PreviewBox {
        DaxButton(
            text = "Dax Button Small",
            size = DaxButtonSize.Small,
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
        )
    }
}

@Composable
@Preview
private fun DaxButtonLargePreview() {
    PreviewBox {
        DaxButton(
            text = "Dax Button Large",
            size = DaxButtonSize.Large,
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
        )
    }
}

@Composable
private fun previewButtonColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Blue,
    contentColor = Color.White,
    disabledContainerColor = Color.LightGray,
    disabledContentColor = Color.DarkGray,
)
