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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Content-agnostic row/column arrangement shared by [DaxListItem] and the design system's
 * skeleton placeholders: paddings, gaps and the leading/content/trailing split, with no knowledge
 * of text, colour, enabled state or click handling.
 *
 * @param minHeight Minimum row height. Callers derive this from their own content since a
 * content-free shell has nothing to derive it from.
 * @param modifier Modifier applied to the outer row.
 * @param contentSpacing Vertical gap between children inside [content].
 * @param leadingContent Optional leading slot, followed by [DaxListItemDefaults.LeadingGap] when present.
 * @param trailingContent Optional trailing slot, preceded by [DaxListItemDefaults.TrailingGap] when present.
 * @param trailingSpacerWidth Width of the spacer reserved at the row's end when [trailingContent] is `null`.
 * @param content The middle, weighted column slot.
 */
@Composable
internal fun DaxListItemLayout(
    minHeight: Dp,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = DaxListItemDefaults.TextSpacing,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    trailingSpacerWidth: Dp = DaxListItemDefaults.HorizontalPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(start = DaxListItemDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(DaxListItemDefaults.LeadingGap))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            modifier = Modifier.weight(1f),
            content = content,
        )

        if (trailingContent != null) {
            Spacer(Modifier.width(DaxListItemDefaults.TrailingGap))
            trailingContent()
        } else {
            Spacer(Modifier.width(trailingSpacerWidth))
        }
    }
}
