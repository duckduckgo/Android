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
 * @param shape The shape of the surface container.
 * @param color The background color of the surface container.
 * @param contentColor The content color of the surface container.
 * @param shadowElevation The size of the shadow below this surface.
 * @param border Optional border to draw around this surface.
 * @param content The content to be displayed inside this surface.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211670215000539
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21488&t=LXCzRHiuFnp4ybLo-4
 */
@Composable
fun DaxSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DuckDuckGoTheme.shapes.medium,
    color: Color = DuckDuckGoTheme.colors.backgrounds.window,
    contentColor: Color = DuckDuckGoTheme.colors.text.primary,
    shadowElevation: Dp = 1.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
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
 * @param shape The shape of the surface container.
 * @param color The background color of the surface container.
 * @param contentColor The content color of the surface container.
 * @param shadowElevation The size of the shadow below this surface.
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
    shape: Shape = DuckDuckGoTheme.shapes.medium,
    color: Color = DuckDuckGoTheme.colors.backgrounds.window,
    contentColor: Color = DuckDuckGoTheme.colors.text.primary,
    shadowElevation: Dp = 1.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
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
