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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DaxCard is a thin wrapper around Material3 Card that applies the default styling for Dax cards.
 *
 * @param modifier The [Modifier] to be applied to this card.
 * @param border Optional border to draw around this card.
 * @param content The content to be displayed inside this card.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211670215000539
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21488&t=LXCzRHiuFnp4ybLo-4
 */
@Composable
fun DaxCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    shape: Shape = DaxCardDefaults.shape,
    elevation: CardElevation = DaxCardDefaults.elevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = DaxCardDefaults.colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

/**
 * DaxCard with click handling is a thin wrapper around Material3 Card that applies the default styling for Dax cards and allows for click handling.
 *
 * @param onClick Callback invoked when the user clicks on the card.
 * @param modifier The [Modifier] to be applied to this card.
 * @param enabled Controls the enabled state of this card.
 * @param border Optional border to draw around this card.
 * @param interactionSource The [MutableInteractionSource] representing the stream of interactions for this card.
 * @param content The content to be displayed inside this card.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211670215000539
 * Figma reference: https://www.figma.com/design/jHLwh4erLbNc2YeobQpGFt/Design-System-Guidelines?node-id=8796-21488&t=LXCzRHiuFnp4ybLo-4
 */
@Composable
fun DaxCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    shape: Shape = DaxCardDefaults.shape,
    elevation: CardElevation = DaxCardDefaults.elevation,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = DaxCardDefaults.colors,
        elevation = elevation,
        border = border,
        content = content,
        enabled = enabled,
        interactionSource = interactionSource,
    )
}

private object DaxCardDefaults {
    /**
     * The default shape for Dax cards is the medium shape from the DuckDuckGo theme.
     */
    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.medium

    /**
     * The default colors for Dax cards are derived from the DuckDuckGo theme, with specific colors for the container, content, and disabled states.
     */
    val colors: CardColors
        @Composable
        get() = CardDefaults.cardColors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.window,
            contentColor = DuckDuckGoTheme.colors.text.primary,
            disabledContainerColor = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
            disabledContentColor = DuckDuckGoTheme.colors.text.disabled,
        )

    /**
     * The default elevation for Dax cards is the card elevation from Material3, which can be customized as needed.
     */
    val elevation: CardElevation
        @Composable
        get() = CardDefaults.cardElevation(defaultElevation = 1.dp)
}

@Composable
@PreviewLightDark
private fun DaxCardPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DaxCard {
                DaxText(
                    text = "Dax Card",
                    modifier = Modifier.padding(10.dp),
                )
            }
            DaxCard(onClick = {}) {
                DaxText(
                    text = "Dax Clickable Card",
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}
