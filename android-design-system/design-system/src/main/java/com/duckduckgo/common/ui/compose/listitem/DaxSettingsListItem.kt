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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Settings row: the list item used throughout the settings screens.
 *
 * Its trailing element is either a [DaxListItemTrailingScope.StatusIndicator] or a
 * [DaxListItemTrailingScope.Icon], so the slot is open like the other variants.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217018486992588
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=12994-10026
 *
 * @param primaryText Primary label.
 * @param modifier Modifier applied to the list item row.
 * @param secondaryText Secondary caption shown beneath the primary label; `null` = one-line layout.
 * @param inlineContent Optional slot rendered inline after the primary text — use [DaxListItemInlineScope] members.
 * @param leadingContent Optional leading slot — use [DaxListItemLeadingScope] members.
 * @param trailingContent Optional trailing slot — use [DaxListItemTrailingScope] members.
 * @param onClick Optional click handler; when non-null the row becomes clickable.
 * @param enabled Whether the row is enabled; disabled rows are dimmed and non-interactive.
 */
@Composable
fun DaxSettingsListItem(
    primaryText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    inlineContent: (@Composable DaxListItemInlineScope.() -> Unit)? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = AnnotatedString(primaryText),
        modifier = modifier,
        secondaryText = secondaryText?.let { AnnotatedString(it) },
        inlineContent = inlineContent,
        primaryMaxLines = Int.MAX_VALUE,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        enabled = enabled,
        minHeight = if (secondaryText == null) DaxListItemDefaults.SettingsMinHeight else Dp.Unspecified,
    )
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemPreview() {
    PreviewSurface {
        DaxSettingsListItem(primaryText = "VPN", trailingContent = { StatusIndicator(Status.On) }, onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemWithPillPreview() {
    PreviewSurface {
        DaxSettingsListItem(
            primaryText = "VPN",
            inlineContent = { Pill("Beta") },
            trailingContent = { StatusIndicator(Status.On) },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemStatusesPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSettingsListItem(primaryText = "On", trailingContent = { StatusIndicator(Status.On) }, onClick = {})
            DaxSettingsListItem(primaryText = "Always on", trailingContent = { StatusIndicator(Status.AlwaysOn) }, onClick = {})
            DaxSettingsListItem(primaryText = "Off", trailingContent = { StatusIndicator(Status.Off) }, onClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemTrailingIconPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSettingsListItem(
                primaryText = "DuckDuckGo on Other Platforms",
                leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null) },
                trailingContent = {
                    Icon(painterResource(R.drawable.ic_open_in_16), "Open", size = DaxListItemTrailingIconSize.Small)
                },
                onClick = {},
            )
            DaxSettingsListItem(
                primaryText = "Subscription",
                secondaryText = "Your Privacy Pro subscription expired",
                leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null) },
                trailingContent = {
                    Icon(painterResource(R.drawable.ic_exclamation_recolorable_16), "Expired", size = DaxListItemTrailingIconSize.Small)
                },
                onClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemLeadingIconAndDisabledPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSettingsListItem(
                primaryText = "With leading icon",
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_globe_24), null, background = DaxListItemIconBackground.Circular)
                },
                trailingContent = { StatusIndicator(Status.On) },
                onClick = {},
            )
            DaxSettingsListItem(
                primaryText = "Disabled",
                trailingContent = { StatusIndicator(Status.Off) },
                enabled = false,
                onClick = {},
            )
        }
    }
}
