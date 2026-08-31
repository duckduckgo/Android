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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed context menu, the Compose counterpart of the XML `com.duckduckgo.common.ui.menu.PopupMenu`.
 *
 * @param anchor The composable the menu is positioned relative to.
 * @param expanded Whether the menu is currently shown.
 * @param onDismissRequest Called when the user dismisses the menu (outside touch, back press, or an item tap).
 * @param modifier Modifier applied to the menu's container.
 * @param content Menu rows — use [DaxContextMenuScope] members.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217926175939990?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=14346-56668&m=dev
 */
@Composable
fun DaxContextMenu(
    anchor: @Composable () -> Unit,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable DaxContextMenuScope.() -> Unit,
) {
    Box {
        anchor()

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = DaxContextMenuDefaults.Offset,
            modifier = modifier.widthIn(min = DaxContextMenuDefaults.MinWidth),
            shape = DaxContextMenuDefaults.Shape,
            containerColor = DaxContextMenuDefaults.ContainerColor,
            tonalElevation = DaxContextMenuDefaults.TonalElevation,
            shadowElevation = DaxContextMenuDefaults.ContainerElevation,
        ) {
            Column {
                CompositionLocalProvider(LocalDaxContextMenuOnDismissRequest provides onDismissRequest) {
                    DaxContextMenuScope.content()
                }
            }
        }
    }
}

/**
 * Convenience [DaxContextMenu] wrapper anchored to a [DaxIconButton], for the common case of a menu triggered
 * by tapping an overflow icon.
 *
 * Unlike [DaxContextMenu], this owns its own expanded state internally rather than hoisting it.
 *
 * @param contentDescription Accessibility description for the anchor button, or null if decorative.
 * @param modifier Modifier applied to the anchor button.
 * @param enabled Whether the anchor button is enabled.
 * @param content Menu rows — use [DaxContextMenuScope] members.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217926175939990?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=14346-56668&m=dev
 */
@Composable
fun DaxContextMenuIconButton(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable DaxContextMenuScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    DaxContextMenu(
        anchor = {
            DaxIconButton(
                onClick = { expanded = true },
                iconPainter = painterResource(R.drawable.ic_menu_vertical_24),
                contentDescription = contentDescription,
                modifier = modifier,
                enabled = enabled,
            )
        },
        expanded = expanded,
        onDismissRequest = { expanded = false },
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun DaxContextMenuIconButtonClosedPreview() {
    PreviewBox {
        DaxContextMenuIconButton(
            contentDescription = "More options",
        ) {
            DaxDefaultItem(text = "Share", onClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxContextMenuIconButtonDisabledPreview() {
    PreviewBox {
        DaxContextMenuIconButton(
            contentDescription = "More options",
            enabled = false,
        ) {
            DaxDefaultItem(text = "Share", onClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxContextMenuIconButtonExpandedPreview() {
    PreviewBox {
        DaxContextMenu(
            anchor = {
                DaxIconButton(
                    onClick = {},
                    iconPainter = painterResource(R.drawable.ic_menu_vertical_24),
                    contentDescription = "More options",
                )
            },
            expanded = true,
            onDismissRequest = {},
        ) {
            DaxIconItem(
                text = "Bookmark",
                painterLeadingIcon = painterResource(R.drawable.ic_bookmark_24),
                onClick = {},
                showDivider = true,
            )
            DaxInsetItem(
                text = "Copy link",
                onClick = {},
                trailingIcon = { Icon(painterResource(R.drawable.ic_copy_24), null) },
                showDivider = true,
            )
            DaxDefaultItem(text = "Delete", onClick = {}, isDestructive = true)
        }
    }
}
