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
 * DuckDuckGo design system loading skeleton shaped like a section header row
 * (see the xml [com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem]).
 *
 * @param modifier the [Modifier] to apply.
 * @param hasTrailingIcon whether to show a trailing circular icon placeholder. Defaults to `false`.
 * @param animated whether the shimmer sweep animates. Set to `false` for static previews or to respect reduced motion.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1217882625977300?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6032-13775&m=dev
 */
@Composable
fun DaxSkeletonSectionHeader(
    modifier: Modifier = Modifier,
    hasTrailingIcon: Boolean = false,
    animated: Boolean = true,
) {
    val verticalPadding = if (hasTrailingIcon) {
        DaxSkeletonSectionHeaderDefaults.PaddingVerticalWithTrailingIcon
    } else {
        DaxSkeletonSectionHeaderDefaults.PaddingVerticalNoTrailingIcon
    }
    val lineWidthFraction = if (hasTrailingIcon) {
        DaxSkeletonSectionHeaderDefaults.LineWidthFractionWithTrailingIcon
    } else {
        DaxSkeletonSectionHeaderDefaults.LineWidthFractionNoTrailingIcon
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DaxSkeletonSectionHeaderDefaults.PaddingHorizontal, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DaxSkeletonLine(modifier = Modifier.fillMaxWidth(lineWidthFraction), animated = animated)
        if (hasTrailingIcon) {
            DaxSkeletonCircle(size = DaxSkeletonSectionHeaderDefaults.TrailingIconSize, animated = animated)
        }
    }
}

internal object DaxSkeletonSectionHeaderDefaults {
    val PaddingHorizontal: Dp = 16.dp
    val PaddingVerticalWithTrailingIcon: Dp = 12.dp
    val PaddingVerticalNoTrailingIcon: Dp = 14.dp
    const val LineWidthFractionWithTrailingIcon: Float = 0.5f
    const val LineWidthFractionNoTrailingIcon: Float = 1f / 3f
    val TrailingIconSize: Dp = 16.dp
}

@PreviewLightDark
@Composable
private fun DaxSkeletonSectionHeaderPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DaxSkeletonSectionHeader()
            DaxSkeletonSectionHeader(hasTrailingIcon = true)
        }
    }
}
