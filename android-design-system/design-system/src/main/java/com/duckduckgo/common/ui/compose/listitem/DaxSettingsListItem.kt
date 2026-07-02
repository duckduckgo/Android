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
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Settings row: one-line list item whose trailing element is a status indicator.
 *
 * The trailing slot is fixed to a [DaxListItemTrailingScope.StatusIndicator] driven by [status]; it is not a free slot like the other variants.
 *
 * @param primaryText Primary label.
 * @param status Status to display in the trailing slot — one of [Status.AlwaysOn], [Status.On], [Status.Off].
 * @param modifier Modifier applied to the list item row.
 * @param pillText Optional pill rendered inline after the primary text; `null` = no pill.
 * @param leadingContent Optional leading slot — use [DaxListItemLeadingScope] members.
 * @param onClick Optional click handler; when non-null the row becomes clickable.
 * @param enabled Whether the row is enabled; disabled rows are dimmed and non-interactive.
 */
@Composable
fun DaxSettingsListItem(
    primaryText: String,
    status: Status,
    modifier: Modifier = Modifier,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = AnnotatedString(primaryText),
        modifier = modifier,
        pillText = pillText,
        primaryMaxLines = Int.MAX_VALUE,
        leadingContent = leadingContent,
        trailingContent = { StatusIndicator(status) },
        onClick = onClick,
        enabled = enabled,
    )
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemPreview() {
    PreviewSurface {
        DaxSettingsListItem(primaryText = "VPN", status = Status.On, onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemWithPillPreview() {
    PreviewSurface {
        DaxSettingsListItem(primaryText = "VPN", status = Status.On, pillText = "Beta", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxSettingsListItemStatusesPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSettingsListItem(primaryText = "On", status = Status.On, onClick = {})
            DaxSettingsListItem(primaryText = "Always on", status = Status.AlwaysOn, onClick = {})
            DaxSettingsListItem(primaryText = "Off", status = Status.Off, onClick = {})
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
                status = Status.On,
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_globe_24), null, background = DaxListItemIconBackground.Circular)
                },
                onClick = {},
            )
            DaxSettingsListItem(primaryText = "Disabled", status = Status.Off, enabled = false, onClick = {})
        }
    }
}
