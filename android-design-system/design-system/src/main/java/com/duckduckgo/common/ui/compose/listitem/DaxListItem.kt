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

@file:OptIn(ExperimentalFoundationApi::class)

package com.duckduckgo.common.ui.compose.listitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import com.duckduckgo.common.ui.compose.DaxStatusIndicator
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.pill.DaxPill
import com.duckduckgo.common.ui.compose.switch.DaxSwitch
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
internal fun DaxListItem(
    primaryText: AnnotatedString,
    modifier: Modifier = Modifier,
    secondaryText: AnnotatedString? = null,
    pillText: String? = null,
    primaryTextColor: Color = DuckDuckGoTheme.textColors.primary,
    secondaryTextColor: Color = DuckDuckGoTheme.textColors.secondary,
    primaryMaxLines: Int = 1,
    secondaryMaxLines: Int = Int.MAX_VALUE,
    leadingContent: (@Composable DaxListItemLeadingScope.() -> Unit)? = null,
    trailingContent: (@Composable DaxListItemTrailingScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val minHeight = when {
        secondaryText != null -> DaxListItemDefaults.TwoLineMinHeight
        leadingContent != null -> DaxListItemDefaults.OneLineWithIconMinHeight
        else -> DaxListItemDefaults.OneLineMinHeight
    }
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }
    val leadingScope = DaxListItemLeadingScope(enabled)
    val trailingScope = DaxListItemTrailingScope(enabled)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .heightIn(min = minHeight)
            .padding(horizontal = DaxListItemDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingScope.leadingContent()
            Spacer(Modifier.width(DaxListItemDefaults.LeadingGap))
        }
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else DaxListItemDefaults.DisabledAlpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DaxText(
                    text = primaryText,
                    style = DuckDuckGoTheme.typography.body1,
                    color = primaryTextColor,
                    maxLines = primaryMaxLines,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pillText != null) {
                    Spacer(Modifier.width(DaxListItemDefaults.PillGap))
                    DaxPill(text = pillText)
                }
            }
            if (secondaryText != null) {
                DaxText(
                    text = secondaryText,
                    style = DuckDuckGoTheme.typography.body2,
                    color = secondaryTextColor,
                    maxLines = secondaryMaxLines,
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(DaxListItemDefaults.TrailingGap))
            trailingScope.trailingContent()
        }
    }
}

@Stable
class DaxListItemLeadingScope internal constructor(private val parentEnabled: Boolean) {
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        size: DaxListItemIconSize = DaxListItemIconSize.Small,
        background: DaxListItemIconBackground = DaxListItemIconBackground.None,
        tint: Color? = DuckDuckGoTheme.colors.icons.primary,
    ) {
        val iconDp = when (size) {
            DaxListItemIconSize.Small -> DaxListItemDefaults.LeadingIconSmall
            DaxListItemIconSize.Large -> DaxListItemDefaults.LeadingIconLarge
        }
        val containerModifier = when (background) {
            DaxListItemIconBackground.None -> modifier.size(iconDp)
            DaxListItemIconBackground.Circular ->
                modifier
                    .size(DaxListItemDefaults.LeadingBackgroundSize)
                    .clip(CircleShape)
                    .background(DuckDuckGoTheme.colors.backgrounds.container)
        }
        Box(
            modifier = containerModifier.alpha(if (parentEnabled) 1f else DaxListItemDefaults.DisabledAlpha),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint ?: Color.Unspecified,
                modifier = Modifier.size(iconDp),
            )
        }
    }
}

@Stable
class DaxListItemTrailingScope internal constructor(private val parentEnabled: Boolean) {
    @Composable
    fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        DaxSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled && parentEnabled)
    }

    /**
     * Trailing icon. [onClick] = null ⇒ decorative (non-clickable); non-null ⇒ clickable.
     *
     * @param tint Honoured only for the decorative case (`onClick == null`). Clickable icons
     * render the painter's own colours via [DaxIconButton], which exposes no tint.
     */
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        size: DaxListItemTrailingIconSize = DaxListItemTrailingIconSize.Medium,
        tint: Color? = null,
        enabled: Boolean = true,
    ) {
        val effectiveEnabled = enabled && parentEnabled
        val iconDp = when (size) {
            DaxListItemTrailingIconSize.Small -> DaxListItemDefaults.TrailingIconSmall
            DaxListItemTrailingIconSize.Medium -> DaxListItemDefaults.TrailingIconMedium
        }
        if (onClick != null) {
            DaxIconButton(
                onClick = onClick,
                iconPainter = painter,
                contentDescription = contentDescription,
                enabled = effectiveEnabled,
                modifier = modifier
                    .size(iconDp)
                    .alpha(if (effectiveEnabled) 1f else DaxListItemDefaults.DisabledAlpha),
            )
        } else {
            androidx.compose.material3.Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint ?: Color.Unspecified,
                modifier = modifier
                    .size(iconDp)
                    .alpha(if (parentEnabled) 1f else DaxListItemDefaults.DisabledAlpha),
            )
        }
    }

    @Composable
    fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        DaxGhostButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled && parentEnabled)
    }

    @Composable
    fun StatusIndicator(status: Status, modifier: Modifier = Modifier) {
        DaxStatusIndicator(status = status, modifier = modifier.alpha(if (parentEnabled) 1f else DaxListItemDefaults.DisabledAlpha))
    }
}
