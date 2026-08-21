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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.DaxStatusIndicator
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxIconButtonDefaults
import com.duckduckgo.common.ui.compose.listitem.DaxListItemDefaults.HorizontalPadding
import com.duckduckgo.common.ui.compose.pill.DaxPill
import com.duckduckgo.common.ui.compose.switch.DaxSwitch
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import androidx.compose.material3.Icon as M3Icon

/**
 * Layout and behaviour core shared by every list-item variant ([DaxOneLineListItem],
 * [DaxTwoLineListItem], [DaxSettingsListItem]).
 *
 * It stays internal: the variants are the public surface and each decides which parameters to
 * expose, wrapping `String` as [AnnotatedString] at its own boundary. A variant restates any
 * rendering-affecting default it relies on rather than inheriting it from here.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217019219692521
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=7394-53145
 *
 * @param primaryText Primary label.
 * @param modifier Modifier applied to the list item row.
 * @param secondaryText Secondary caption shown beneath the primary label; `null` = one-line layout.
 * @param inlineContent Optional slot rendered inline after the primary text — use [DaxListItemInlineScope] members.
 * @param primaryTextColor Primary label colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param secondaryTextColor Secondary caption colour; must be a [DuckDuckGoTheme] colour (lint-enforced).
 * @param primaryMaxLines Maximum lines for the primary label.
 * @param secondaryMaxLines Maximum lines for the secondary caption.
 * @param leadingContent Optional leading slot — use [DaxListItemLeadingScope] members.
 * @param trailingContent Optional trailing slot — use [DaxListItemTrailingScope] members.
 * @param onClick Optional click handler; when non-null the row becomes clickable.
 * @param onLongClick Optional long-click handler; when non-null the row becomes long-clickable.
 * @param enabled Whether the row is enabled and interactive. Disabled rows are dimmed, and the
 * leading and trailing scopes pass the state on to their members so slot content is disabled rather
 * than only dimmed.
 */
@Composable
internal fun DaxListItem(
    primaryText: AnnotatedString,
    modifier: Modifier = Modifier,
    secondaryText: AnnotatedString? = null,
    inlineContent: (@Composable DaxListItemInlineScope.() -> Unit)? = null,
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
            .padding(start = HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingScope.leadingContent()
            Spacer(Modifier.width(DaxListItemDefaults.LeadingGap))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else DaxListItemDefaults.DisabledAlpha),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DaxText(
                    text = primaryText,
                    style = DuckDuckGoTheme.typography.body1,
                    color = primaryTextColor,
                    maxLines = primaryMaxLines,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (inlineContent != null) {
                    Spacer(Modifier.width(DaxListItemDefaults.PillGap))
                    DaxListItemInlineScope.inlineContent()
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
        } else {
            Spacer(Modifier.width(HorizontalPadding))
        }
    }
}

/**
 * Receiver scope for a list item's leading slot.
 *
 * Typing the slot as a receiver on this scope is what restricts leading content to the design
 * system's own composables; the `DaxListItemContentDetector` lint rule enforces it at build time.
 *
 * Members combine the owning row's enabled state with their own, so a disabled row disables its
 * slot content rather than only dimming it.
 */
@Stable
class DaxListItemLeadingScope internal constructor(private val parentEnabled: Boolean) {

    /**
     * Leading icon.
     *
     * @param painter Icon artwork.
     * @param contentDescription Accessibility description; `null` for a decorative icon.
     * @param modifier Modifier applied to the icon container.
     * @param size Icon size; independent of [background].
     * @param background Container drawn behind the icon; independent of [size].
     * @param tint Icon tint; `null` renders the painter's own colours, for artwork that carries
     * them already (e.g. a favicon).
     */
    @Composable
    fun Icon(
        painter: Painter,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        size: DaxListItemIconSize = DaxListItemIconSize.Small,
        background: DaxListItemIconBackground = DaxListItemIconBackground.None,
        tint: Color? = null,
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
            M3Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint ?: Color.Unspecified,
                modifier = Modifier.size(iconDp),
            )
        }
    }
}

/**
 * Receiver scope for the slot rendered inline after a list item's primary text.
 *
 * Typing the slot as a receiver on this scope is what restricts inline content to the design
 * system's own composables; the `DaxListItemContentDetector` lint rule enforces it at build time.
 *
 * Members take no enabled state: the slot sits inside the text column, which the row already dims
 * as a whole when disabled.
 */
@Stable
object DaxListItemInlineScope {

    @Composable
    fun Pill(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        DaxPill(text = text, modifier = modifier)
    }
}

/**
 * Receiver scope for a list item's trailing slot.
 *
 * Typing the slot as a receiver on this scope is what restricts trailing content to the design
 * system's own composables; the `DaxListItemContentDetector` lint rule enforces it at build time.
 *
 * Members combine the owning row's enabled state with their own, so a disabled row disables its
 * slot content rather than only dimming it.
 */
@Stable
class DaxListItemTrailingScope internal constructor(private val parentEnabled: Boolean) {

    /**
     * Trailing switch.
     *
     * @param checked Whether the switch is on.
     * @param onCheckedChange Called when the user toggles the switch; `null` = read-only.
     * @param modifier Modifier applied to the switch.
     * @param enabled Whether the switch itself is enabled, on top of the row's enabled state.
     */
    @Composable
    fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        Row {
            DaxSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled && parentEnabled)

            Spacer(Modifier.width(HorizontalPadding))
        }
    }

    /**
     * Trailing icon. [onClick] = null ⇒ decorative (non-clickable); non-null ⇒ clickable.
     *
     * @param painter Icon artwork.
     * @param contentDescription Accessibility description; `null` for a decorative icon.
     * @param modifier Modifier applied to the icon.
     * @param onClick Called when the icon is tapped; `null` makes the icon decorative.
     * @param size Icon size.
     * @param tint Honoured only for the decorative case (`onClick == null`). Clickable icons
     * render the painter's own colours via [DaxIconButton], which exposes no tint.
     * @param enabled Whether the icon itself is enabled, on top of the row's enabled state.
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
        Row {
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
                    iconSize = iconDp,
                    colors = DaxIconButtonDefaults.iconButtonColors.copy(contentColor = Color.Unspecified),
                    modifier = modifier.size(DaxListItemDefaults.TrailingIconTouchTarget),
                )

                Spacer(Modifier.width(HorizontalPadding - (DaxListItemDefaults.TrailingIconTouchTarget - iconDp) / 2))
            } else {
                M3Icon(
                    painter = painter,
                    contentDescription = contentDescription,
                    tint = tint ?: Color.Unspecified,
                    modifier = modifier
                        .size(iconDp)
                        .alpha(if (parentEnabled) 1f else DaxListItemDefaults.DisabledAlpha),
                )

                Spacer(Modifier.width(HorizontalPadding))
            }
        }
    }

    /**
     * Trailing ghost button.
     *
     * @param text Button label.
     * @param onClick Called when the button is tapped.
     * @param modifier Modifier applied to the button.
     * @param enabled Whether the button itself is enabled, on top of the row's enabled state.
     */
    @Composable
    fun Button(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        DaxGhostButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled && parentEnabled)
    }

    /**
     * Trailing status indicator, the fixed trailing element of [DaxSettingsListItem].
     *
     * @param status Status to display.
     * @param modifier Modifier applied to the indicator.
     */
    @Composable
    fun StatusIndicator(
        status: Status,
        modifier: Modifier = Modifier,
    ) {
        Row {
            DaxStatusIndicator(status = status, modifier = modifier.alpha(if (parentEnabled) 1f else DaxListItemDefaults.DisabledAlpha))

            Spacer(Modifier.width(HorizontalPadding))
        }
    }
}
