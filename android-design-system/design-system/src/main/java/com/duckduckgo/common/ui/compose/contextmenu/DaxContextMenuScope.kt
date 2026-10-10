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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/**
 * Composition local carrying the enclosing [DaxContextMenu]'s `onDismissRequest`, so [DaxContextMenuScope]'s
 * members can dismiss the menu after firing the caller's `onClick`. [DaxContextMenuScope] is a singleton
 * object, so it cannot carry that callback as instance state the way `DaxContextMenuItemTrailingScope` does.
 */
internal val LocalDaxContextMenuOnDismissRequest = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Receiver scope for a [DaxContextMenu]'s content: the only way to add rows to the menu, restricting
 * content to the design system's own context-menu items and ensuring every row dismisses the menu once
 * tapped.
 */
@Stable
object DaxContextMenuScope {

    /**
     * Delegates to [DaxDefaultContextMenuItem], dismissing the enclosing menu after [onClick].
     *
     * @param text Item label.
     * @param onClick Called when the row is tapped, before the enclosing menu is dismissed.
     * @param modifier Modifier applied to the row.
     * @param showDivider Whether a horizontal divider is rendered below the row.
     * @param isDestructive Whether the label colours to the destructive token.
     * @param enabled Whether the row is enabled and interactive.
     * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
     */
    @Composable
    fun DaxDefaultItem(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showDivider: Boolean = false,
        isDestructive: Boolean = false,
        enabled: Boolean = true,
        trailingIcon: (@Composable DaxContextMenuItemTrailingScope.() -> Unit)? = null,
    ) {
        val onDismissRequest = LocalDaxContextMenuOnDismissRequest.current
        DaxDefaultContextMenuItem(
            text = text,
            onClick = {
                onClick()
                onDismissRequest()
            },
            modifier = modifier,
            showDivider = showDivider,
            isDestructive = isDestructive,
            enabled = enabled,
            trailingIcon = trailingIcon,
        )
    }

    /**
     * Delegates to [DaxIconContextMenuItem], dismissing the enclosing menu after [onClick].
     *
     * @param text Item label.
     * @param painterLeadingIcon Leading icon artwork.
     * @param onClick Called when the row is tapped, before the enclosing menu is dismissed.
     * @param modifier Modifier applied to the row.
     * @param showDivider Whether a horizontal divider is rendered below the row.
     * @param isDestructive Whether the label and leading icon colour to the destructive token.
     * @param enabled Whether the row is enabled and interactive.
     * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
     */
    @Composable
    fun DaxIconItem(
        text: String,
        painterLeadingIcon: Painter,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showDivider: Boolean = false,
        isDestructive: Boolean = false,
        enabled: Boolean = true,
        trailingIcon: (@Composable DaxContextMenuItemTrailingScope.() -> Unit)? = null,
    ) {
        val onDismissRequest = LocalDaxContextMenuOnDismissRequest.current
        DaxIconContextMenuItem(
            text = text,
            painterLeadingIcon = painterLeadingIcon,
            onClick = {
                onClick()
                onDismissRequest()
            },
            modifier = modifier,
            showDivider = showDivider,
            isDestructive = isDestructive,
            enabled = enabled,
            trailingIcon = trailingIcon,
        )
    }

    /**
     * Delegates to [DaxInsetContextMenuItem], dismissing the enclosing menu after [onClick].
     *
     * @param text Item label.
     * @param onClick Called when the row is tapped, before the enclosing menu is dismissed.
     * @param modifier Modifier applied to the row.
     * @param showDivider Whether a horizontal divider is rendered below the row.
     * @param isDestructive Whether the label colours to the destructive token.
     * @param enabled Whether the row is enabled and interactive.
     * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
     */
    @Composable
    fun DaxInsetItem(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showDivider: Boolean = false,
        isDestructive: Boolean = false,
        enabled: Boolean = true,
        trailingIcon: (@Composable DaxContextMenuItemTrailingScope.() -> Unit)? = null,
    ) {
        val onDismissRequest = LocalDaxContextMenuOnDismissRequest.current
        DaxInsetContextMenuItem(
            text = text,
            onClick = {
                onClick()
                onDismissRequest()
            },
            modifier = modifier,
            showDivider = showDivider,
            isDestructive = isDestructive,
            enabled = enabled,
            trailingIcon = trailingIcon,
        )
    }
}
