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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Single-line list item for the DuckDuckGo design system.
 *
 * The primary label wraps by default (font-scaling safe); pass `primaryMaxLines = 1` to truncate with an ellipsis.
 *
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6032-14026
 *
 * @param primaryText Primary label.
 * @param modifier Modifier applied to the list item container.
 * @param primaryTextColor Text colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param pillText Optional pill rendered inline after the primary text; `null` = no pill.
 * @param leadingContent Optional leading slot — use [DaxListItemLeadingScope] members.
 * @param trailingContent Optional trailing slot — use [DaxListItemTrailingScope] members.
 * @param onClick Optional click handler; when non-null the item becomes clickable.
 * @param onLongClick Optional long-click handler.
 * @param primaryMaxLines Maximum lines for the primary label; defaults to [Int.MAX_VALUE] (wraps).
 * @param enabled Whether the item is enabled and interactive; defaults to true.
 */
@Composable
fun DaxOneLineListItem(
    primaryText: String,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    primaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = AnnotatedString(primaryText),
        modifier = modifier,
        pillText = pillText,
        primaryTextColor = primaryTextColor,
        primaryMaxLines = primaryMaxLines,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
    )
}

/**
 * [AnnotatedString] primary-text variant of [DaxOneLineListItem] for HTML / inline-styled labels.
 *
 * @param primaryText Annotated primary label supporting inline spans / links.
 * @see DaxOneLineListItem for the remaining parameters.
 */
@Composable
fun DaxOneLineListItem(
    primaryText: AnnotatedString,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    primaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = primaryText,
        modifier = modifier,
        pillText = pillText,
        primaryTextColor = primaryTextColor,
        primaryMaxLines = primaryMaxLines,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
    )
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemPreview() {
    PreviewSurface {
        DaxOneLineListItem(primaryText = "Primary label", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemWithIconAndSwitchPreview() {
    PreviewSurface {
        DaxOneLineListItem(
            primaryText = "Wi-Fi",
            leadingContent = {
                Icon(painterResource(R.drawable.ic_add_24_solid_color), null, background = DaxListItemIconBackground.Circular)
            },
            trailingContent = { Switch(checked = true, onCheckedChange = {}) },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemWithPillPreview() {
    PreviewSurface {
        DaxOneLineListItem(primaryText = "Feature", pillText = "New", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemDestructivePreview() {
    PreviewSurface {
        DaxOneLineListItem(primaryText = "Delete", primaryTextColor = DuckDuckGoTheme.textColors.destructive, onClick = {})
    }
}

@PreviewFontScale
@Composable
private fun DaxOneLineListItemFontScalePreview() {
    PreviewSurface {
        DaxOneLineListItem(primaryText = "Primary label at scale", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemAnnotatedPreview() {
    PreviewSurface {
        DaxOneLineListItem(
            primaryText = buildAnnotatedString {
                append("Annotated ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("primary") }
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemLeadingIconsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxOneLineListItem(
                primaryText = "Small icon",
                leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Large icon",
                leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null, size = DaxListItemIconSize.Large) },
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Small circular icon",
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.ic_globe_24),
                        null,
                        size = DaxListItemIconSize.Small,
                        background = DaxListItemIconBackground.Circular,
                    )
                },
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Large circular icon",
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.ic_globe_24),
                        null,
                        size = DaxListItemIconSize.Large,
                        background = DaxListItemIconBackground.Circular,
                    )
                },
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Favicon (untinted image)",
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_ddg_logo), null, size = DaxListItemIconSize.Large, tint = null)
                },
                onClick = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemTrailingVariantsPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxOneLineListItem(
                primaryText = "Decorative trailing icon",
                trailingContent = { Icon(painterResource(R.drawable.ic_globe_24), null) },
            )
            DaxOneLineListItem(
                primaryText = "Clickable trailing icon",
                trailingContent = { Icon(painterResource(R.drawable.ic_union), "Overflow", onClick = {}) },
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Tinted trailing icon",
                trailingContent = {
                    Icon(painterResource(R.drawable.ic_globe_24), null, tint = DuckDuckGoTheme.colors.brand.accentBlue)
                },
            )
            DaxOneLineListItem(
                primaryText = "Trailing button",
                trailingContent = { Button(text = "Action", onClick = {}) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemStatesPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxOneLineListItem(primaryText = "Disabled", enabled = false, onClick = {})
            DaxOneLineListItem(
                primaryText = "Disabled with checked switch",
                enabled = false,
                trailingContent = { Switch(checked = true, onCheckedChange = {}) },
            )
            DaxOneLineListItem(
                primaryText = "Switch-only disabled",
                trailingContent = { Switch(checked = true, onCheckedChange = {}, enabled = false) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxOneLineListItemTextBehaviourPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxOneLineListItem(
                primaryText = "Long primary text that wraps onto multiple lines because the default maxLines is unbounded",
                onClick = {},
            )
            DaxOneLineListItem(
                primaryText = "Long primary text that truncates with an ellipsis because maxLines is one",
                primaryMaxLines = 1,
                onClick = {},
            )
            DaxOneLineListItem(primaryText = "Accent", primaryTextColor = DuckDuckGoTheme.colors.brand.accentBlue, onClick = {})
        }
    }
}
