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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenuIconButton
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenuScope
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.Black48
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.White48
import com.duckduckgo.common.ui.compose.tools.PreviewSurface

/**
 * Section header row for the DuckDuckGo design system. A title header with an
 * optional overflow menu icon at the end.
 *
 * @param title Header title
 * @param modifier Modifier applied to the header
 * @param overflowMenuEnabled Whether the overflow icon is enabled
 * @param overflowMenuContentDescription Accessibility description for the overflow icon
 * @param overflowMenuContent Rows of the menu opened by tapping the overflow icon — use [DaxContextMenuScope]
 * members. If null, the overflow icon will not be displayed
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1218945139643877?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=2800-3898
 */
@Composable
fun DaxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overflowMenuEnabled: Boolean = true,
    overflowMenuContentDescription: String? = null,
    overflowMenuContent: (@Composable DaxContextMenuScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DaxSectionHeaderDefaults.MinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaxText(
            text = title,
            style = DaxSectionHeaderDefaults.titleStyle,
            color = DaxSectionHeaderDefaults.titleColor,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = DaxSectionHeaderDefaults.HorizontalPadding,
                    top = DaxSectionHeaderDefaults.VerticalPadding,
                    bottom = DaxSectionHeaderDefaults.VerticalPadding,
                    end = if (overflowMenuContent != null) {
                        DaxSectionHeaderDefaults.TitleToOverflowMenuGap
                    } else {
                        DaxSectionHeaderDefaults.HorizontalPadding
                    },
                ),
        )

        if (overflowMenuContent != null) {
            DaxContextMenuIconButton(
                contentDescription = overflowMenuContentDescription,
                enabled = overflowMenuEnabled,
                modifier = Modifier
                    .padding(end = DaxSectionHeaderDefaults.OverflowMenuEndPadding)
                    .size(DaxSectionHeaderDefaults.OverflowMenuButtonSize),
                content = overflowMenuContent,
            )
        }
    }
}

internal object DaxSectionHeaderDefaults {
    val MinHeight: Dp = 48.dp
    val HorizontalPadding: Dp = 16.dp
    val VerticalPadding: Dp = 16.dp
    val TitleToOverflowMenuGap: Dp = 10.dp
    val OverflowMenuEndPadding: Dp = 4.dp
    val OverflowMenuButtonSize: Dp = 36.dp

    val titleStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.h4

    val titleColor: Color
        @Composable @ReadOnlyComposable
        get() = if (DuckDuckGoTheme.colors.isDark) White48 else Black48
}

@PreviewLightDark
@Composable
private fun DaxSectionHeaderPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSectionHeader(title = "Title")
            DaxSectionHeader(
                title = "Title",
                overflowMenuContentDescription = "More options",
            ) {
                DaxDefaultItem(text = "Share", onClick = {})
            }
            DaxSectionHeader(
                title = "Title",
                overflowMenuEnabled = false,
                overflowMenuContentDescription = "More options",
            ) {
                DaxDefaultItem(text = "Share", onClick = {})
            }
        }
    }
}

@PreviewFontScale
@Composable
private fun DaxSectionHeaderFontScalePreview() {
    PreviewSurface {
        DaxSectionHeader(
            title = "Header text",
            overflowMenuContentDescription = "More options",
        ) {
            DaxDefaultItem(text = "Share", onClick = {})
        }
    }
}
