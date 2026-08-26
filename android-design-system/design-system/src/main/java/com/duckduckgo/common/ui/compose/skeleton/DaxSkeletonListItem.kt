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

package com.duckduckgo.common.ui.compose.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system loading skeleton shaped like a [com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem]
 * / [com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem] row.
 *
 * @param modifier the [Modifier] to apply.
 * @param hasLeadingIcon whether to show the leading circular placeholder. Defaults to `true`.
 * @param hasTwoLines whether to show a shorter secondary line under the primary one. Defaults to `false`.
 * @param animated whether the shimmer sweep animates. Set to `false` for static previews or to respect reduced motion.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217882625977300?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6032-13775&m=dev
 */
@Composable
fun DaxSkeletonListItem(
    modifier: Modifier = Modifier,
    hasLeadingIcon: Boolean = true,
    hasTwoLines: Boolean = false,
    animated: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DaxSkeletonListItemDefaults.PaddingStart,
                end = DaxSkeletonListItemDefaults.PaddingEnd,
                top = DaxSkeletonListItemDefaults.PaddingVertical,
                bottom = DaxSkeletonListItemDefaults.PaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaxSkeletonListItemDefaults.LeadingToLineGap),
    ) {
        if (hasLeadingIcon) {
            DaxSkeletonCircle(animated = animated)
        }
        if (hasTwoLines) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DaxSkeletonListItemDefaults.LineGap),
            ) {
                DaxSkeletonLine(modifier = Modifier.fillMaxWidth(), animated = animated)
                DaxSkeletonLine(
                    modifier = Modifier.fillMaxWidth(DaxSkeletonListItemDefaults.SecondaryLineWidthFraction),
                    animated = animated,
                )
            }
        } else {
            DaxSkeletonLine(modifier = Modifier.weight(1f), animated = animated)
        }
    }
}

internal object DaxSkeletonListItemDefaults {
    val PaddingStart: Dp = 16.dp
    val PaddingEnd: Dp = 64.dp
    val PaddingVertical: Dp = 8.dp
    val LeadingToLineGap: Dp = 16.dp
    val LineGap: Dp = 4.dp
    const val SecondaryLineWidthFraction: Float = 0.5f
}

@PreviewLightDark
@Composable
private fun DaxSkeletonListItemPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DaxSkeletonListItem()
            DaxSkeletonListItem(hasTwoLines = true)
            DaxSkeletonListItem(hasLeadingIcon = false)
        }
    }
}
