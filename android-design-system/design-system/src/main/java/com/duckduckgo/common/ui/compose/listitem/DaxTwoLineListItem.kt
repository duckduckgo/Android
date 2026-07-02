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
 * Two-line list item for the DuckDuckGo design system.
 *
 * The primary label defaults to a single ellipsised line; the secondary caption is unbounded (wraps),
 * matching the View. Sibling overloads accept [AnnotatedString] for HTML / inline-styled text.
 *
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6032-13665
 *
 * @param primaryText Primary label.
 * @param secondaryText Secondary caption shown beneath the primary label.
 * @param modifier Modifier applied to the list item container.
 * @param primaryTextColor Primary label colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param secondaryTextColor Secondary caption colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param pillText Optional pill rendered inline after the primary text; `null` = no pill.
 * @param leadingContent Optional leading slot — use [DaxListItemLeadingScope] members.
 * @param trailingContent Optional trailing slot — use [DaxListItemTrailingScope] members.
 * @param onClick Optional click handler; when set the item becomes clickable.
 * @param onLongClick Optional long-click handler.
 * @param primaryMaxLines Maximum lines for the primary label; defaults to 1.
 * @param secondaryMaxLines Maximum lines for the secondary caption; defaults to [Int.MAX_VALUE].
 * @param enabled Whether the item is enabled and interactive; defaults to true.
 */
@Composable
fun DaxTwoLineListItem(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    secondaryTextColor: Color = DuckDuckGoTheme.textColors.secondary,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    primaryMaxLines: Int = 1,
    secondaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = AnnotatedString(primaryText),
        modifier = modifier,
        secondaryText = AnnotatedString(secondaryText),
        pillText = pillText,
        primaryTextColor = primaryTextColor,
        secondaryTextColor = secondaryTextColor,
        primaryMaxLines = primaryMaxLines,
        secondaryMaxLines = secondaryMaxLines,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
    )
}

/**
 * Two-line list item with an [AnnotatedString] secondary caption (links / inline styling).
 *
 * @see DaxTwoLineListItem for the parameters.
 */
@Composable
fun DaxTwoLineListItem(
    primaryText: String,
    secondaryText: AnnotatedString,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    secondaryTextColor: Color = DuckDuckGoTheme.textColors.secondary,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    primaryMaxLines: Int = 1,
    secondaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = AnnotatedString(primaryText),
        modifier = modifier,
        secondaryText = secondaryText,
        pillText = pillText,
        primaryTextColor = primaryTextColor,
        secondaryTextColor = secondaryTextColor,
        primaryMaxLines = primaryMaxLines,
        secondaryMaxLines = secondaryMaxLines,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
    )
}

/**
 * Two-line list item with [AnnotatedString] primary **and** secondary text — the HTML-in-both-lines case.
 *
 * @see DaxTwoLineListItem for the parameters.
 */
@Composable
fun DaxTwoLineListItem(
    primaryText: AnnotatedString,
    secondaryText: AnnotatedString,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    secondaryTextColor: Color = DuckDuckGoTheme.textColors.secondary,
    pillText: String? = null,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    primaryMaxLines: Int = 1,
    secondaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    DaxListItem(
        primaryText = primaryText,
        modifier = modifier,
        secondaryText = secondaryText,
        pillText = pillText,
        primaryTextColor = primaryTextColor,
        secondaryTextColor = secondaryTextColor,
        primaryMaxLines = primaryMaxLines,
        secondaryMaxLines = secondaryMaxLines,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
    )
}

@PreviewLightDark
@Composable
private fun DaxTwoLineListItemPreview() {
    PreviewSurface {
        DaxTwoLineListItem(primaryText = "Primary label", secondaryText = "Secondary caption", onClick = {})
    }
}

@PreviewFontScale
@Composable
private fun DaxTwoLineListItemFontScalePreview() {
    PreviewSurface {
        DaxTwoLineListItem(primaryText = "Primary label", secondaryText = "Secondary caption", onClick = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxTwoLineListItemAnnotatedSecondaryPreview() {
    PreviewSurface {
        DaxTwoLineListItem(
            primaryText = "Primary label",
            secondaryText = buildAnnotatedString {
                append("Annotated ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("secondary") }
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTwoLineListItemAnnotatedBothPreview() {
    PreviewSurface {
        DaxTwoLineListItem(
            primaryText = buildAnnotatedString {
                append("Annotated ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("primary") }
            },
            secondaryText = buildAnnotatedString {
                append("Annotated ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("secondary") }
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTwoLineListItemLeadingAndTrailingPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxTwoLineListItem(
                primaryText = "With leading icon",
                secondaryText = "Supporting text",
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_globe_24), null, background = DaxListItemIconBackground.Circular)
                },
                onClick = {},
            )
            DaxTwoLineListItem(
                primaryText = "With switch",
                secondaryText = "Supporting text",
                trailingContent = { Switch(checked = true, onCheckedChange = {}) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxTwoLineListItemPillAndUnboundedPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxTwoLineListItem(primaryText = "With Beta pill", secondaryText = "Supporting text", pillText = "Beta", onClick = {})
            DaxTwoLineListItem(
                primaryText = "Unbounded secondary",
                secondaryText = "This supporting caption is intentionally long so it wraps over several lines, " +
                    "showing the unbounded secondary default that matches the View.",
                onClick = {},
            )
        }
    }
}
