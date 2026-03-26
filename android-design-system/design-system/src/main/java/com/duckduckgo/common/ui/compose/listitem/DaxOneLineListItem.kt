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

package com.duckduckgo.common.ui.compose.listitem

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

/**
 * TODO When list item will be available in the design system, replace this with the official DaxOneListListItem composable.
 */
@Composable
fun DaxOneLineListItem(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (DaxListItemTrailingIconScope.() -> Unit)? = null,
    containerColor: Color = DuckDuckGoTheme.colors.backgrounds.surface,
    contentColor: Color = DuckDuckGoTheme.colors.text.primary,
) {
    val colors = ListItemDefaults.colors(
        containerColor = containerColor,
        headlineColor = contentColor,
        leadingIconColor = contentColor,
        trailingIconColor = contentColor,
    )
    ListItem(
        headlineContent = { DaxText(text) },
        leadingContent = if (leadingIcon != null) {
            {
                DaxListItemTrailingIconScope.leadingIcon()
            }
        } else {
            null
        },
        colors = colors,
        modifier = modifier.height(height = 48.dp),
    )
}

object DaxListItemTrailingIconScope {
    @Composable
    fun DaxListItemTrailingIcon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = enabled,
            modifier = modifier,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = DuckDuckGoTheme.colors.icons.primary,
            )
        }
    }
}
