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

package com.duckduckgo.common.ui.compose.contextmenu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

internal object DaxContextMenuItemDefaults {
    val VerticalPadding: Dp = 12.dp
    val ContentGap: Dp = 16.dp
    val DividerSpacing: Dp = 4.dp

    val DefaultStartPadding: Dp = 16.dp
    val DefaultEndPadding: Dp = 16.dp

    val IconStartPadding: Dp = 12.dp
    val IconEndPadding: Dp = 16.dp
    val LeadingIconSize: Dp = 24.dp

    val InsetStartPadding: Dp = 48.dp
    val InsetEndPadding: Dp = 16.dp

    val TrailingIconSize: Dp = 24.dp

    val textStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body1

    @Composable
    fun textColor(enabled: Boolean, isDestructive: Boolean): Color = when {
        !enabled -> DuckDuckGoTheme.textColors.disabled
        isDestructive -> DuckDuckGoTheme.textColors.destructive
        else -> DuckDuckGoTheme.textColors.primary
    }

    val colors: DaxContextMenuItemColors
        @Composable
        get() = DaxContextMenuItemColors(
            icon = DuckDuckGoTheme.iconColors.primary,
            disabledIcon = DuckDuckGoTheme.iconColors.disabled,
        )
}

@Immutable
internal data class DaxContextMenuItemColors(
    val icon: Color,
    val disabledIcon: Color,
)
