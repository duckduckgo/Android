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

package com.duckduckgo.common.ui.compose.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed icon button.
 *
 * Wraps Material3 [IconButton] for icon-only actions (e.g. close, back, overflow).
 *
 * The icon is tinted with [colors]'s content color so it follows the Compose
 * [DuckDuckGoTheme] (light/dark) rather than the drawable's baked-in colors. Pass
 * [DaxIconButtonDefaults.filledColors] for a filled container.
 *
 * @param onClick Called when the button is clicked.
 * @param iconPainter The icon to display.
 * @param contentDescription Accessibility description, or null if decorative.
 * @param modifier Modifier for this button.
 * @param enabled Whether the button is enabled.
 * @param colors Container and content colors; defaults to [DaxIconButtonDefaults.colors].
 * @param interactionSource The interaction source for this button.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215540472063931?focus=true
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxIconButton(
    onClick: () -> Unit,
    iconPainter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = DaxIconButtonDefaults.colors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            enabled = enabled,
            interactionSource = interactionSource,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = if (enabled) {
                    colors.contentColor
                } else {
                    colors.disabledContentColor
                },
            )
        }
    }
}

object DaxIconButtonDefaults {
    val colors: IconButtonColors
        @Composable
        get() = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = DuckDuckGoTheme.colors.icons.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = DuckDuckGoTheme.colors.icons.disabled,
        )

    val filledColors: IconButtonColors
        @Composable
        get() = IconButtonDefaults.iconButtonColors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.container,
            contentColor = DuckDuckGoTheme.colors.icons.primary,
            disabledContainerColor = DuckDuckGoTheme.colors.backgrounds.container,
            disabledContentColor = DuckDuckGoTheme.colors.icons.disabled,
        )
}

@PreviewLightDark
@Composable
private fun DaxIconButtonPreview() {
    PreviewBox {
        Column {
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Settings",
            )
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Settings",
                colors = DaxIconButtonDefaults.filledColors,
            )
        }
    }
}
