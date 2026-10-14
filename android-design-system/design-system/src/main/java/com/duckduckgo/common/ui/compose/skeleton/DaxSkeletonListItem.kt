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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.listitem.DaxListItemDefaults
import com.duckduckgo.common.ui.compose.listitem.DaxListItemLayout
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
    val rowMinHeight = when {
        hasTwoLines -> DaxListItemDefaults.TwoLineMinHeight
        hasLeadingIcon -> DaxListItemDefaults.OneLineWithIconMinHeight
        else -> DaxListItemDefaults.OneLineMinHeight
    }
    DaxListItemLayout(
        minHeight = rowMinHeight,
        modifier = modifier.daxSkeletonShimmer(animated),
        contentSpacing = DaxSkeletonListItemDefaults.LineGap,
        leadingContent = if (hasLeadingIcon) { { DaxSkeletonCircle() } } else null,
        trailingSpacerWidth = DaxSkeletonListItemDefaults.PaddingEnd,
    ) {
        DaxSkeletonLine(modifier = Modifier.fillMaxWidth())
        if (hasTwoLines) {
            DaxSkeletonLine(
                modifier = Modifier.fillMaxWidth(DaxSkeletonListItemDefaults.SecondaryLineWidthFraction),
            )
        }
    }
}

internal object DaxSkeletonListItemDefaults {
    val PaddingEnd: Dp = 64.dp
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
