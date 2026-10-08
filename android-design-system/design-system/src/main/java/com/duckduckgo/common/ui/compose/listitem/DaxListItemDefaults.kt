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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

internal object DaxListItemDefaults {
    val OneLineMinHeight: Dp = 48.dp
    val OneLineWithIconMinHeight: Dp = 56.dp
    val TwoLineMinHeight: Dp = 64.dp
    val SettingsMinHeight: Dp = 48.dp

    val HorizontalPadding: Dp = 16.dp
    val LeadingGap: Dp = 16.dp
    val TrailingGap: Dp = 16.dp
    val PillGap: Dp = 8.dp
    val TextSpacing: Dp = 2.dp

    val LeadingIconSmall: Dp = 24.dp
    val LeadingIconLarge: Dp = 32.dp
    val LeadingBackgroundSize: Dp = 40.dp
    val LeadingRoundedBackgroundRadius: Dp = 8.dp

    val TrailingIconMedium: Dp = 24.dp
    val TrailingIconSmall: Dp = 16.dp
    val TrailingIconFrame: Dp = 24.dp
    val TrailingIconTouchTarget: Dp = 48.dp

    const val DisabledAlpha: Float = 0.4f

    val colors: DaxListItemColors
        @Composable
        get() = DaxListItemColors(
            primaryText = DuckDuckGoTheme.textColors.primary,
            secondaryText = DuckDuckGoTheme.textColors.secondary,
            leadingIconBackground = DuckDuckGoTheme.colors.backgrounds.container,
        )
}

@Immutable
internal data class DaxListItemColors(
    val primaryText: Color,
    val secondaryText: Color,
    val leadingIconBackground: Color,
)

@Stable
enum class DaxListItemIconBackground { None, Circular, Rounded }

@Stable
enum class DaxListItemIconSize { Small, Large }

@Stable
enum class DaxListItemTrailingIconSize { Small, Medium }
