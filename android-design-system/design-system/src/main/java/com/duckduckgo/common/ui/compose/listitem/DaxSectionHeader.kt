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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R

/**
 * Section header row for the DuckDuckGo design system. A title header with an
 * optional overflow menu icon at the end.
 *
 * @param title Header title
 * @param modifier Modifier applied to the header
 * @param overflowMenuClickListener Called when the overflow icon is tapped. If null, the overflow icon will
 * not be displayed
 * @param overflowMenuContentDescription Accessibility description for the overflow icon
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1218945139643877?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=2800-3898
 */
@Composable
fun DaxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overflowMenuClickListener: (() -> Unit)? = null,
    overflowMenuContentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaxText(
            text = title,
            style = DuckDuckGoTheme.typography.h4,
            color = DuckDuckGoTheme.textColors.tertiary,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                    end = if (overflowMenuClickListener != null) {
                        10.dp
                    } else {
                        16.dp
                    },
                ),
        )

        if (overflowMenuClickListener != null) {
            DaxIconButton(
                onClick = overflowMenuClickListener,
                iconPainter = painterResource(R.drawable.ic_menu_vertical_24),
                contentDescription = overflowMenuContentDescription,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(36.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxSectionHeaderPreview() {
    PreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSectionHeader(title = "Title")
            DaxSectionHeader(
                title = "Title",
                overflowMenuClickListener = {},
                overflowMenuContentDescription = "More options",
            )
        }
    }
}

@PreviewFontScale
@Composable
private fun DaxSectionHeaderFontScalePreview() {
    PreviewSurface {
        DaxSectionHeader(
            title = "Header text",
            overflowMenuClickListener = {},
            overflowMenuContentDescription = "More options",
        )
    }
}
