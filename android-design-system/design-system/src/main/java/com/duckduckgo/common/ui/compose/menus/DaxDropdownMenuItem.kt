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

package com.duckduckgo.common.ui.compose.menus

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DaxDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    DropdownMenuItem(
        modifier = modifier,
        text = { DaxText(text) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = DaxDropdownMenuItemDefault.colors,
        enabled = enabled,
        onClick = onClick
    )
}

object DaxDropdownMenuItemDefault {
    val colors: MenuItemColors
        @Composable
        get() = MenuItemColors(
            textColor = DuckDuckGoTheme.colors.text.primary,
            disabledTextColor = DuckDuckGoTheme.colors.text.disabled,
            leadingIconColor = DuckDuckGoTheme.colors.icons.primary,
            disabledLeadingIconColor = DuckDuckGoTheme.colors.icons.disabled,
            trailingIconColor = DuckDuckGoTheme.colors.icons.primary,
            disabledTrailingIconColor = DuckDuckGoTheme.colors.icons.disabled,
        )
}
