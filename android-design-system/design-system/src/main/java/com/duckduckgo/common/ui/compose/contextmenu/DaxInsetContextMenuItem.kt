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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.duckduckgo.common.ui.compose.button.DaxButtonStateParameterProvider
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Context-menu item inset to align its label with a sibling [DaxIconContextMenuItem]'s text,
 * for use when a row has no leading icon of its own but sits in a menu where other rows do.
 *
 * @param text Item label.
 * @param onClick Called when the row is tapped.
 * @param modifier Modifier applied to the row.
 * @param showDivider Whether a horizontal divider is rendered below the row.
 * @param isDestructive Whether the label colours to the destructive token.
 * @param enabled Whether the row is enabled and interactive.
 * @param trailingIcon Optional trailing slot — use [DaxContextMenuItemTrailingScope] members.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217926175939990?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=14346-56668&m=dev
 */
@Composable
fun DaxInsetContextMenuItem(
    text: String,
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
        startPadding = DaxContextMenuItemDefaults.InsetStartPadding,
        endPadding = DaxContextMenuItemDefaults.InsetEndPadding,
        isDestructive = isDestructive,
        enabled = enabled,
        trailingIcon = trailingIcon,
        showDivider = showDivider,
    )
}

@PreviewLightDark
@Composable
private fun DaxInsetContextMenuItemPreview(
    @PreviewParameter(DaxButtonStateParameterProvider::class) enabled: Boolean,
) {
    PreviewSurface {
        DaxInsetContextMenuItem(text = "Share", onClick = {}, enabled = enabled)
    }
}

@PreviewLightDark
@Composable
private fun DaxInsetContextMenuItemWithTrailingIconPreview() {
    PreviewSurface {
        DaxInsetContextMenuItem(
            text = "Copy link",
            onClick = {},
            trailingIcon = { Icon(painterResource(R.drawable.ic_copy_24), null) },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxInsetContextMenuItemDestructivePreview() {
    PreviewSurface {
        DaxInsetContextMenuItem(text = "Remove", onClick = {}, isDestructive = true)
    }
}

@PreviewLightDark
@Composable
private fun DaxInsetContextMenuItemWithDividerPreview() {
    PreviewSurface {
        Column {
            DaxInsetContextMenuItem(text = "Share", onClick = {}, showDivider = true)
            DaxInsetContextMenuItem(text = "Remove", onClick = {}, isDestructive = true)
        }
    }
}
