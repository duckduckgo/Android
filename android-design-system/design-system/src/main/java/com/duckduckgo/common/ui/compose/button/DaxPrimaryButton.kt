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
import com.duckduckgo.common.ui.compose.theme.Black6
import com.duckduckgo.common.ui.compose.theme.Black84
import com.duckduckgo.common.ui.compose.theme.Blue50
import com.duckduckgo.common.ui.compose.theme.Blue70
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White
import com.duckduckgo.common.ui.compose.theme.White6
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo primary button with filled background.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1213826087792399
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=12110-29649&t=ZNNQ3qIoJQfTS14R-4
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
fun DaxPrimaryButton(
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
        colors = DaxPrimaryButtonDefaults.colors(),
        rippleConfiguration = DaxPrimaryButtonDefaults.rippleConfiguration(),
        leadingIconPainter = leadingIconPainter,
        modifier = modifier,
        enabled = enabled,
    )
}

private object DaxPrimaryButtonDefaults {

    val adsColorButtonPrimaryContainer: Color
        @Composable @ReadOnlyComposable get() = DuckDuckGoTheme.colors.brand.accentBlue

    val adsColorButtonPrimaryContainerPressed: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Blue50
        } else {
            Blue70
        }

    val adsColorButtonPrimaryText: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            Black84
        } else {
            White
        }

    val adsColorButtonPrimaryContainerDisabled: Color
        @Composable @ReadOnlyComposable get() = if (DuckDuckGoTheme.colors.isDark) {
            White6
        } else {
            Black6
        }

    val adsColorButtonPrimaryTextDisabled: Color
        @Composable @ReadOnlyComposable get() = DuckDuckGoTheme.textColors.disabled

    @Composable @ReadOnlyComposable
    fun colors(): DaxButtonColors = DaxButtonColors(
        containerColor = adsColorButtonPrimaryContainer,
        contentColor = adsColorButtonPrimaryText,
        disabledContainerColor = adsColorButtonPrimaryContainerDisabled,
        disabledContentColor = adsColorButtonPrimaryTextDisabled,
    )

    @Composable
    fun rippleConfiguration(): RippleConfiguration {
        val color = adsColorButtonPrimaryContainerPressed
        return remember(color) { RippleConfiguration(color = color) }
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryButtonSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryButton(
            text = "Primary Small",
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryButtonLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryButton(
            text = "Primary Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryButtonWithIconSmallPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryButton(
            text = "Primary Small",
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryButtonWithIconLargePreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewBox {
        DaxPrimaryButton(
            text = "Primary Large",
            size = DaxButtonSize.Large,
            onClick = { },
            enabled = enabled,
            leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color),
        )
    }
}
