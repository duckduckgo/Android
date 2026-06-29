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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * [DaxIconButtonDefaults.filledIconButtonColors] for a filled container.
 *
 * @param onClick Called when the button is clicked.
 * @param iconPainter The icon to display.
 * @param contentDescription Accessibility description, or null if decorative.
 * @param modifier Modifier for this button.
 * @param enabled Whether the button is enabled.
 * @param colors Container and content colors; defaults to [DaxIconButtonDefaults.iconButtonColors].
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
    colors: DaxIconButtonColors = DaxIconButtonDefaults.iconButtonColors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
            ),
            enabled = enabled,
            interactionSource = interactionSource,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
            )
        }
    }
}

object DaxIconButtonDefaults {
    val iconButtonColors: DaxIconButtonColors
        @Composable
        get() = DaxIconButtonColors(
            containerColor = Color.Unspecified,
            contentColor = DuckDuckGoTheme.colors.icons.primary,
            disabledContainerColor = Color.Unspecified,
            disabledContentColor = DuckDuckGoTheme.colors.icons.disabled,
        )

    val filledIconButtonColors: DaxIconButtonColors
        @Composable
        get() = DaxIconButtonColors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.container,
            contentColor = DuckDuckGoTheme.colors.icons.primary,
            disabledContainerColor = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
            disabledContentColor = DuckDuckGoTheme.colors.icons.disabled,
        )
}

@Immutable
data class DaxIconButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

@PreviewLightDark
@Composable
private fun DaxIconButtonPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Settings",
            )
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Settings",
                colors = DaxIconButtonDefaults.filledIconButtonColors,
            )
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Settings",
                enabled = false,
                colors = DaxIconButtonDefaults.filledIconButtonColors,
            )
        }
    }
}
