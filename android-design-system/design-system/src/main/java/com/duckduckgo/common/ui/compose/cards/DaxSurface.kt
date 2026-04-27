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

package com.duckduckgo.common.ui.compose.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DaxSurface is a thin wrapper around Material3 Surface that applies the default styling for Dax cards.
 *
 * @param modifier The [Modifier] to be applied to this surface.
 * @param border Optional border to draw around this surface.
 * @param content The content to be displayed inside this surface.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211670215000539
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21488&t=LXCzRHiuFnp4ybLo-4
 */
@Composable
fun DaxSurface(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = DaxSurfaceDefaults.shape,
        color = DaxSurfaceDefaults.color,
        contentColor = DaxSurfaceDefaults.contentColor,
        shadowElevation = DaxSurfaceDefaults.elevation,
        border = border,
        content = content,
    )
}

/**
 * DaxSurface with click handling is a thin wrapper around Material3 Surface that applies the default styling for Dax cards and allows for click
 * handling.
 *
 * @param onClick Callback invoked when this surface is clicked.
 * @param modifier The [Modifier] to be applied to this surface.
 * @param enabled Controls the enabled state of this surface.
 * @param border Optional border to draw around this surface.
 * @param interactionSource The [MutableInteractionSource] representing the stream of interactions for this surface.
 * @param content The content to be displayed inside this surface.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211670215000539
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21488&t=LXCzRHiuFnp4ybLo-4
 */
@Composable
fun DaxSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = DaxSurfaceDefaults.shape,
        color = DaxSurfaceDefaults.color,
        contentColor = DaxSurfaceDefaults.contentColor,
        shadowElevation = DaxSurfaceDefaults.elevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

private object DaxSurfaceDefaults {
    /**
     * The default shape for Dax surfaces is the medium shape from the DuckDuckGo theme, which provides a balanced appearance that works well for card-like components.
     */
    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.medium

    /**
     * The default colors for Dax surfaces are derived from the DuckDuckGo theme.
     */
    val color: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.window

    /**
     * The default content color for Dax surfaces is the primary text color from the DuckDuckGo theme.
     */
    val contentColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.primary

    /**
     * The default elevation for Dax surfaces is 1.dp, which provides a subtle shadow to help the surface stand out from the background without being too prominent.
     */
    val elevation: Dp
        @Composable
        get() = 1.dp
}

@Composable
@PreviewLightDark
private fun DaxSurfacePreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DaxSurface {
                DaxText(
                    text = "Dax Surface",
                    modifier = Modifier.padding(10.dp),
                )
            }
            DaxSurface(onClick = {}) {
                DaxText(
                    text = "Clickable Dax Surface",
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
