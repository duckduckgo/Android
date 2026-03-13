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
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.RippleAlpha
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkDefault18
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkPressed
import com.duckduckgo.common.ui.compose.theme.AlertRedOnDarkTextPressed
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightDefault18
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightPressed
import com.duckduckgo.common.ui.compose.theme.AlertRedOnLightTextPressed
import com.duckduckgo.common.ui.compose.theme.Black12
import com.duckduckgo.common.ui.compose.theme.Black6
import com.duckduckgo.common.ui.compose.theme.Black60
import com.duckduckgo.common.ui.compose.theme.Black84
import com.duckduckgo.common.ui.compose.theme.Blue20
import com.duckduckgo.common.ui.compose.theme.Blue30
import com.duckduckgo.common.ui.compose.theme.Blue30_20
import com.duckduckgo.common.ui.compose.theme.Blue50
import com.duckduckgo.common.ui.compose.theme.Blue50_12
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.theme.White12
import com.duckduckgo.common.ui.compose.theme.White24
import com.duckduckgo.common.ui.compose.theme.White6
import com.duckduckgo.common.ui.compose.theme.White84
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Defines the type/style of a DaxButton.
 */
enum class DaxButtonType {
    PRIMARY,
    SECONDARY,
    GHOST,
    DESTRUCTIVE_PRIMARY,
    DESTRUCTIVE_GHOST,
    DESTRUCTIVE_GHOST_SECONDARY,
}

/**
 * Defines the size of a DaxButton.
 */
enum class DaxButtonSize {
    SMALL,
    LARGE,
}

/**
 * DuckDuckGo button component with support for multiple types and sizes.
 *
 * @param text The text to display on the button.
 * @param onClick Called when the button is clicked.
 * @param type The type/style of the button (e.g., PRIMARY, SECONDARY, GHOST).
 * @param modifier The modifier to be applied to the button.
 * @param size The size of the button (SMALL or LARGE).
 * @param enabled Whether the button is enabled.
 * @param leadingIcon Optional leading icon painter.
 */
@Composable
fun DaxButton(
    text: String,
    onClick: () -> Unit,
    type: DaxButtonType,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.SMALL,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
) {
    val colors = resolveButtonColors(type)
    val rippleConfiguration = resolveRippleConfiguration(type)
    val border = resolveBorder(type, enabled)

    DaxButtonInternal(
        onClick = onClick,
        colors = colors,
        rippleConfiguration = rippleConfiguration,
        modifier = modifier,
        border = border,
        enabled = enabled,
        size = size,
        leadingIcon = leadingIcon,
    ) {
        DaxButtonText(text)
    }
}

/**
 * Internal button implementation that handles size variations.
 *
 * This is *not* part of the design system and should *only* be used internally
 * by button implementations.
 */
@Composable
internal fun DaxButtonInternal(
    onClick: () -> Unit,
    colors: DaxButtonColors,
    rippleConfiguration: RippleConfiguration,
    size: DaxButtonSize,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val (height, contentPadding) = when (size) {
        DaxButtonSize.SMALL -> dimensionResource(R.dimen.buttonSmallHeight) to PaddingValues(
            horizontal = dimensionResource(R.dimen.buttonSmallSidePadding),
            vertical = dimensionResource(R.dimen.buttonSmallTopPadding),
        )
        DaxButtonSize.LARGE -> dimensionResource(R.dimen.buttonLargeHeight) to PaddingValues(
            horizontal = dimensionResource(R.dimen.buttonLargeSidePadding),
            vertical = dimensionResource(R.dimen.buttonLargeTopPadding),
        )
    }

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

// ========================================
// Color and Configuration Resolvers
// ========================================

@Composable
internal fun resolveButtonColors(type: DaxButtonType): DaxButtonColors {
    return when (type) {
        DaxButtonType.PRIMARY -> primaryColors()
        DaxButtonType.SECONDARY -> secondaryColors()
        DaxButtonType.GHOST -> ghostColors()
        DaxButtonType.DESTRUCTIVE_PRIMARY -> destructivePrimaryColors()
        DaxButtonType.DESTRUCTIVE_GHOST -> destructiveGhostColors()
        DaxButtonType.DESTRUCTIVE_GHOST_SECONDARY -> destructiveGhostSecondaryColors()
    }
}

@Composable
internal fun resolveRippleConfiguration(type: DaxButtonType): RippleConfiguration {
    return when (type) {
        DaxButtonType.PRIMARY -> primaryButtonRippleConfiguration()
        DaxButtonType.SECONDARY -> secondaryButtonRippleConfiguration()
        DaxButtonType.GHOST -> ghostButtonRippleConfiguration()
        DaxButtonType.DESTRUCTIVE_PRIMARY -> destructivePrimaryButtonRippleConfiguration()
        DaxButtonType.DESTRUCTIVE_GHOST -> destructiveGhostButtonRippleConfiguration()
        DaxButtonType.DESTRUCTIVE_GHOST_SECONDARY -> destructiveGhostSecondaryButtonRippleConfiguration()
    }
}

@Composable
internal fun resolveBorder(type: DaxButtonType, enabled: Boolean): BorderStroke? {
    return when (type) {
        DaxButtonType.SECONDARY -> BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                DuckDuckGoTheme.colors.brand.accentBlue
            } else {
                if (DuckDuckGoTheme.colors.isDark) White24 else Black12
            },
        )
        else -> null
    }
}

// ========================================
// Primary Button Colors
// ========================================

@Composable
private fun primaryColors(): DaxButtonColors = DaxButtonColors(
    containerColor = DuckDuckGoTheme.colors.brand.accentBlue,
    contentColor = if (DuckDuckGoTheme.colors.isDark) Black84 else White,
    disabledContainerColor = if (DuckDuckGoTheme.colors.isDark) White6 else Black6,
    disabledContentColor = DuckDuckGoTheme.textColors.disabled,
)

@Composable
private fun primaryButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) Blue50 else Blue70,
        rippleAlpha = RippleAlpha(
            pressedAlpha = 1f,
            focusedAlpha = 0.24f,
            draggedAlpha = 0.16f,
            hoveredAlpha = 0.08f,
        ),
    )

// ========================================
// Secondary Button Colors
// ========================================

@Composable
private fun secondaryColors(): DaxButtonColors =
    DaxButtonColors(
        containerColor = Color.Transparent,
        contentColor = if (DuckDuckGoTheme.colors.isDark) Blue30 else Blue50,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = DuckDuckGoTheme.textColors.disabled,
        pressedContentColor = if (DuckDuckGoTheme.colors.isDark) Blue20 else Blue70,
    )

@Composable
private fun secondaryButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) Blue30_20 else Blue50_12,
    )

// ========================================
// Ghost Button Colors
// ========================================

@Composable
private fun ghostColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Transparent,
    contentColor = DuckDuckGoTheme.colors.brand.accentBlue,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = DuckDuckGoTheme.textColors.disabled,
    pressedContentColor = if (DuckDuckGoTheme.colors.isDark) Blue20 else Blue70,
)

@Composable
private fun ghostButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) Blue30_20 else Blue50_12,
    )

// ========================================
// Destructive Primary Button Colors
// ========================================

@Composable
private fun destructivePrimaryColors(): DaxButtonColors = DaxButtonColors(
    containerColor = DuckDuckGoTheme.colors.status.criticalPrimary,
    contentColor = if (DuckDuckGoTheme.colors.isDark) Black84 else White,
    disabledContainerColor = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
    disabledContentColor = DuckDuckGoTheme.textColors.disabled,
)

@Composable
private fun destructivePrimaryButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) AlertRedOnDarkPressed else AlertRedOnLightPressed,
    )

// ========================================
// Destructive Ghost Button Colors
// ========================================

@Composable
private fun destructiveGhostColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Transparent,
    contentColor = DuckDuckGoTheme.colors.status.criticalPrimary,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = DuckDuckGoTheme.textColors.disabled,
    pressedContentColor = if (DuckDuckGoTheme.colors.isDark) AlertRedOnDarkTextPressed else AlertRedOnLightTextPressed,
)

@Composable
private fun destructiveGhostButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) AlertRedOnDarkDefault18 else AlertRedOnLightDefault18,
    )

// ========================================
// Destructive Ghost Secondary Button Colors
// ========================================

@Composable
private fun destructiveGhostSecondaryColors(): DaxButtonColors = DaxButtonColors(
    containerColor = Color.Transparent,
    contentColor = if (DuckDuckGoTheme.colors.isDark) White84 else Black60,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = DuckDuckGoTheme.textColors.disabled,
    pressedContentColor = if (DuckDuckGoTheme.colors.isDark) White84 else Black60,
)

@Composable
private fun destructiveGhostSecondaryButtonRippleConfiguration() =
    RippleConfiguration(
        color = if (DuckDuckGoTheme.colors.isDark) White12 else Black6,
    )

// ========================================
// Previews
// ========================================

@PreviewLightDark
@Composable
private fun DaxButtonAllTypesPreview() {
    PreviewBox {
        Column(modifier = Modifier.padding(16.dp)) {
            DaxButton(
                text = "Primary",
                onClick = {},
                type = DaxButtonType.PRIMARY,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Secondary",
                onClick = {},
                type = DaxButtonType.SECONDARY,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Ghost",
                onClick = {},
                type = DaxButtonType.GHOST,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Destructive Primary",
                onClick = {},
                type = DaxButtonType.DESTRUCTIVE_PRIMARY,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Destructive Ghost",
                onClick = {},
                type = DaxButtonType.DESTRUCTIVE_GHOST,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Destructive Ghost Secondary",
                onClick = {},
                type = DaxButtonType.DESTRUCTIVE_GHOST_SECONDARY,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonSizesPreview() {
    PreviewBox {
        Column(modifier = Modifier.padding(16.dp)) {
            DaxButton(
                text = "Small Primary",
                onClick = {},
                type = DaxButtonType.PRIMARY,
                size = DaxButtonSize.SMALL,
            )
            Spacer(modifier = Modifier.size(8.dp))
            DaxButton(
                text = "Large Primary",
                onClick = {},
                type = DaxButtonType.PRIMARY,
                size = DaxButtonSize.LARGE,
            )
        }
    }
}
