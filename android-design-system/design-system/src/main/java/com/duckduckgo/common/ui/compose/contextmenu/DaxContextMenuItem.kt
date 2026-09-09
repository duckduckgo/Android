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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import androidx.compose.material3.Icon as M3Icon

/**
 * Layout core shared by every context-menu-item variant ([DaxDefaultContextMenuItem],
 * [DaxIconContextMenuItem], [DaxInsetContextMenuItem]).
 *
 * @param text Item label.
 * @param onClick Called when the row is tapped.
 * @param modifier Modifier applied to the row.
 * @param startPadding Leading edge padding; distinguishes the three public variants.
 * @param endPadding Trailing edge padding.
 * @param leadingIcon Optional leading icon artwork, only rendered by [DaxIconContextMenuItem].
 * @param showDivider Whether a [DaxHorizontalDivider] is rendered below the row.
 * @param isDestructive Whether the text defaults colour to the destructive token.
 * @param enabled Whether the row is enabled and interactive. Disabled rows use the disabled text and
 * icon tokens, and the trailing scope passes the state on to its members.
 * @param textColor Label colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
 */
@Composable
internal fun DaxContextMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = DaxContextMenuItemDefaults.DefaultStartPadding,
    endPadding: Dp = DaxContextMenuItemDefaults.DefaultEndPadding,
    leadingIcon: Painter? = null,
    showDivider: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    textColor: Color = DaxContextMenuItemDefaults.textColor(enabled = enabled, isDestructive = isDestructive),
    trailingIcon: (@Composable DaxContextMenuItemTrailingScope.() -> Unit)? = null,
) {
    val colors = DaxContextMenuItemDefaults.colors
    val trailingScope = DaxContextMenuItemTrailingScope(enabled)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    start = startPadding,
                    end = endPadding,
                    top = DaxContextMenuItemDefaults.VerticalPadding,
                    bottom = DaxContextMenuItemDefaults.VerticalPadding,
                ),
        ) {
            if (leadingIcon != null) {
                M3Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) colors.icon else colors.disabledIcon,
                    modifier = Modifier.size(DaxContextMenuItemDefaults.LeadingIconSize),
                )
                Spacer(Modifier.width(DaxContextMenuItemDefaults.ContentGap))
            }

            DaxText(
                text = text,
                style = DaxContextMenuItemDefaults.textStyle,
                color = textColor,
                modifier = Modifier.weight(1f),
            )

            if (trailingIcon != null) {
                Spacer(Modifier.width(DaxContextMenuItemDefaults.ContentGap))
                trailingScope.trailingIcon()
            }
        }

        if (showDivider) {
            Spacer(Modifier.height(DaxContextMenuItemDefaults.DividerSpacing))
            DaxHorizontalDivider()
            Spacer(Modifier.height(DaxContextMenuItemDefaults.DividerSpacing))
        }
    }
}

/**
 * Receiver scope for a context-menu item's trailing slot.
 *
 * Typing the slot as a receiver on this scope restricts trailing content to the design system's
 * own composables, matching the pattern used by `DaxListItemTrailingScope`.
 *
 * @param parentEnabled Whether the owning item is enabled; carried through so the scope's [Icon]
 * defaults to the disabled icon tint when the parent item is disabled.
 */
@Stable
class DaxContextMenuItemTrailingScope internal constructor(
    private val parentEnabled: Boolean,
) {

    /**
     * Trailing icon, fixed at the context-menu-item trailing size.
     *
     * @param painter Icon artwork.
     * @param contentDescription Accessibility description; `null` for a decorative icon.
     * @param modifier Modifier applied to the icon.
     * @param tint Icon tint; defaults to the disabled icon token when the parent item is disabled.
     */
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        tint: Color = if (parentEnabled) DaxContextMenuItemDefaults.colors.icon else DaxContextMenuItemDefaults.colors.disabledIcon,
    ) {
        M3Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(DaxContextMenuItemDefaults.TrailingIconSize),
        )
    }
}
