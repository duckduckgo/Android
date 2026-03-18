/*
 * Copyright (c) 2026 DuckDuckGo
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo base button with custom content.
 *
 * This is *not* part of the design system and should *only* be used when creating a
 * *new* design system compliant button.
 *
 * See [DaxSecondaryButton] and [DaxGhostButton] for example usage.
 *
 * @param onClick Called when the button is clicked.
 * @param size The button size — [DaxButtonSize.Small] or [DaxButtonSize.Large].
 * @param colors The button color scheme, including content and container colors.
 * @param rippleConfiguration The ripple effect configuration for the button.
 * @param modifier Modifier for this button.
 * @param border Optional border stroke drawn around the button.
 * @param enabled Whether the button is enabled for interaction.
 * @param content The content displayed inside the button.
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val dimensions = resolveButtonDimensions(size = size)

    CompositionLocalProvider(
        LocalRippleConfiguration provides rippleConfiguration,
    ) {
        Button(
            onClick = onClick,
            modifier = modifier
                .padding(vertical = dimensions.insetVertical)
                .height(dimensions.surfaceHeight),
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
            interactionSource = interactionSource,
            content = content,
        )
    }
}

/**
 * DuckDuckGo base button with text and optional leading icon.
 *
 * This is *not* part of the design system and should *only* be used when creating a
 * *new* design system compliant button.
 *
 * @param text The button label.
 * @param onClick Called when the button is clicked.
 * @param size The button size — [DaxButtonSize.Small] or [DaxButtonSize.Large].
 * @param colors The button color scheme, including content and container colors.
 * @param rippleConfiguration The ripple effect configuration for the button.
 * @param modifier Modifier for this button.
 * @param leadingIconPainter Optional icon [Painter] displayed before the button text.
 *   When non-null, renders a 16dp ([DaxButtonSize.Small]) or 24dp ([DaxButtonSize.Large])
 *   icon tinted with the button's content color.
 *   Pass `null` (default) for a text-only button.
 * @param border Optional border stroke drawn around the button.
 * @param enabled Whether the button is enabled for interaction.
 */
@Composable
internal fun DaxButton(
    text: String,
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    leadingIconPainter: Painter? = null,
    border: BorderStroke? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val contentColor = when {
        !enabled -> colors.disabledContentColor
        isPressed -> colors.pressedContentColor
        else -> colors.contentColor
    }

    DaxButton(
        onClick = onClick,
        size = size,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        modifier = modifier,
        border = border,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        if (leadingIconPainter != null) {
            DaxButtonIcon(
                painter = leadingIconPainter,
                tint = contentColor,
                modifier = Modifier.size(if (size == DaxButtonSize.Small) SmallIconSize else LargeIconSize),
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.keyline_2)))
        }
        DaxButtonText(
            text = text,
            color = contentColor,
        )
    }
}

@Composable
private fun resolveButtonDimensions(size: DaxButtonSize): DaxButtonDimensions {
    return when (size) {
        DaxButtonSize.Small -> DaxButtonDimensions(
            surfaceHeight = SmallButtonSurfaceHeight,
            insetVertical = ButtonInsetVertical,
            contentPadding = PaddingValues(
                horizontal = dimensionResource(R.dimen.buttonSmallSidePadding),
            ),
        )

        DaxButtonSize.Large -> DaxButtonDimensions(
            surfaceHeight = LargeButtonSurfaceHeight,
            insetVertical = ButtonInsetVertical,
            contentPadding = PaddingValues(
                horizontal = dimensionResource(R.dimen.buttonLargeSidePadding),
            ),
        )
    }
}

@Composable
private fun DaxButtonText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    DaxText(
        text = text,
        style = DuckDuckGoTheme.typography.button,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun DaxButtonIcon(
    painter: Painter,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Immutable
internal data class DaxButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val pressedContentColor: Color = contentColor,
)

@Stable
enum class DaxButtonSize {
    Small,
    Large,
}

private val ButtonInsetVertical = 6.dp
private val SmallButtonSurfaceHeight = 36.dp
private val LargeButtonSurfaceHeight = 48.dp
private val SmallIconSize = 16.dp
private val LargeIconSize = 24.dp

@Immutable
private data class DaxButtonDimensions(
    val surfaceHeight: Dp,
    val insetVertical: Dp,
    val contentPadding: PaddingValues,
)

internal class DaxButtonStateParameterProvider : PreviewParameterProvider<Boolean> {
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

@Preview
@Composable
private fun DaxButtonWithIconSmallPreview() {
    PreviewBox {
        DaxButton(
            text = "Dax Button Small",
            size = DaxButtonSize.Small,
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}

@Preview
@Composable
private fun DaxButtonWithIconLargePreview() {
    PreviewBox {
        DaxButton(
            text = "Dax Button Large",
            size = DaxButtonSize.Large,
            onClick = {},
            colors = previewButtonColors(),
            rippleConfiguration = RippleConfiguration(),
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
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
