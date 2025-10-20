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
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
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
internal fun DaxButton(
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
    leadingIcon: Painter? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val contentColor = if (isPressed) colors.pressedContentColor else colors.contentColor

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
        Button(
            onClick = onClick,
            modifier = modifier.height(height),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.containerColor,
                contentColor = contentColor,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
            ),
            border = border,
            enabled = enabled,
            shape = DuckDuckGoTheme.shapes.medium,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            content = {
                if (leadingIcon != null) {
                    Image(
                        painter = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                content()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaxButtonLarge(
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    DaxButton(
        onClick = onClick,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        border = border,
        modifier = modifier,
        enabled = enabled,
        height = dimensionResource(R.dimen.buttonLargeHeight),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(R.dimen.buttonLargeSidePadding),
            vertical = dimensionResource(R.dimen.buttonLargeTopPadding),
        ),
        content = content,
    )
}

@Composable
internal fun DaxButtonText(
    text: String,
    modifier: Modifier = Modifier
) {
    DaxText(
        text = text,
        style = DuckDuckGoTheme.typography.button,
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

class DaxButtonStateParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(
        true,
        false,
    )
}
