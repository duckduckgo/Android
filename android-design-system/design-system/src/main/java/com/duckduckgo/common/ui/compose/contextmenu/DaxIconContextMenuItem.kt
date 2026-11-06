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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.duckduckgo.common.ui.compose.button.DaxButtonStateParameterProvider
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Context-menu item with a leading icon.
 *
 * @param text Item label.
 * @param painterLeadingIcon Leading icon artwork.
 * @param onClick Called when the row is tapped.
 * @param modifier Modifier applied to the row.
 * @param showDivider Whether a horizontal divider is rendered below the row.
 * @param isDestructive Whether the label and leading icon colour to the destructive token.
 * @param enabled Whether the row is enabled and interactive.
 * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217926175939990?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=14346-56668&m=dev
 */
@Composable
fun DaxIconContextMenuItem(
    text: String,
    painterLeadingIcon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: (@Composable DaxContextMenuItemTrailingScope.() -> Unit)? = null,
) {
    DaxContextMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        startPadding = DaxContextMenuItemDefaults.IconStartPadding,
        endPadding = DaxContextMenuItemDefaults.IconEndPadding,
        leadingIcon = painterLeadingIcon,
        isDestructive = isDestructive,
        enabled = enabled,
        trailingIcon = trailingIcon,
        showDivider = showDivider,
    )
}

@PreviewLightDark
@Composable
private fun DaxIconContextMenuItemPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewSurface {
        DaxIconContextMenuItem(
            text = "Bookmark",
            painterLeadingIcon = painterResource(R.drawable.ic_bookmark_24),
            onClick = {},
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxIconContextMenuItemWithTrailingIconPreview() {
    PreviewSurface {
        DaxIconContextMenuItem(
            text = "Open in new tab",
            painterLeadingIcon = painterResource(R.drawable.ic_globe_24),
            onClick = {},
            trailingIcon = { Icon(painterResource(R.drawable.ic_open_in_16), null) },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxIconContextMenuItemDestructivePreview() {
    PreviewSurface {
        DaxIconContextMenuItem(
            text = "Delete",
            painterLeadingIcon = painterResource(R.drawable.ic_trash_24),
            onClick = {},
            isDestructive = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxIconContextMenuItemWithDividerPreview() {
    PreviewSurface {
        Column {
            DaxIconContextMenuItem(
                text = "Bookmark",
                painterLeadingIcon = painterResource(R.drawable.ic_bookmark_24),
                onClick = {},
                showDivider = true,
            )
            DaxIconContextMenuItem(
                text = "Delete",
                painterLeadingIcon = painterResource(R.drawable.ic_trash_24),
                onClick = {},
                isDestructive = true,
            )
        }
    }
}
