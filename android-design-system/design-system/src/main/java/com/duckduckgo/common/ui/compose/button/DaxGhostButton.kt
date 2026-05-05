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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.duckduckgo.common.ui.compose.theme.Blue20
import com.duckduckgo.common.ui.compose.theme.Blue30_20
import com.duckduckgo.common.ui.compose.theme.Blue50_12
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo ghost button with transparent background.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1213826087792401
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=12110-29651&t=ZNNQ3qIoJQfTS14R-4
 *
 * @param text The button label.
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier for this button.
 * @param size The button size — [DaxButtonSize.Small] or [DaxButtonSize.Large].
 * @param enabled Whether the button is enabled for interaction.
 * @param leadingIconPainter Optional icon [Painter] displayed before the button text.
 *   When non-null, renders a 16dp ([DaxButtonSize.Small]) or 24dp ([DaxButtonSize.Large])
 *   icon tinted with the button's content color.
 *   Pass `null` (default) for a text-only button.
 */
@Composable
fun DaxGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DaxButtonSize = DaxButtonSize.Small,
    enabled: Boolean = true,
    leadingIconPainter: Painter? = null,
) {
    DaxButton(
        text = text,
        onClick = onClick,
        size = size,
        colors = DaxGhostButtonDefaults.colors(),
        rippleConfiguration = DaxGhostButtonDefaults.rippleConfiguration(),
        leadingIconPainter = leadingIconPainter,
        modifier = modifier,
        enabled = enabled,
    )
}

private object DaxGhostButtonDefaults {

    val adsColorButtonGhostContainer: Color
        @Composable @ReadOnlyComposable get() = Color.Transparent

    val adsColorButtonGhostContainerPressed: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Blue30_20
        } else {
            Blue50_12
        }

    val adsColorButtonGhostText: Color
        @Composable @ReadOnlyComposable get() = DuckDuckGoTheme.colors.brand.accentBlue

    val adsColorButtonGhostTextPressed: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Blue20
        } else {
            Blue70
        }

    val adsColorButtonGhostDisabled: Color
        @Composable @ReadOnlyComposable get() = DuckDuckGoTheme.textColors.disabled

    @Composable @ReadOnlyComposable
    fun colors(): DaxButtonColors = DaxButtonColors(
        containerColor = adsColorButtonGhostContainer,
        contentColor = adsColorButtonGhostText,
        disabledContainerColor = adsColorButtonGhostContainer,
        disabledContentColor = adsColorButtonGhostDisabled,
        pressedContentColor = adsColorButtonGhostTextPressed,
    )

    @Composable
    fun rippleConfiguration(): RippleConfiguration {
        val color = adsColorButtonGhostContainerPressed
        return remember(color) { RippleConfiguration(color = color) }
    }
}

@PreviewLightDark
@Composable
private fun DaxGhostButtonSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxGhostButton(
            text = "Ghost Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxGhostButtonLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxGhostButton(
            text = "Ghost Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxGhostButtonWithIconSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxGhostButton(
            text = "Ghost Small",
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxGhostButtonWithIconLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxGhostButton(
            text = "Ghost Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}
