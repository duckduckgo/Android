/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.ui.widget.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
internal fun DaxColorAttributeListItem(
    text: String,
    dotColors: DaxColorDotColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaxText(
            text = text,
            style = DuckDuckGoTheme.typography.body1,
        )

        Spacer(modifier = Modifier.weight(1f))

        DaxColorDot(
            colors = dotColors,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxColorAttributeListItemPreview() {
    PreviewBox {
        DaxColorAttributeListItem(
            text = "DaxColorAttributeListItem",
            dotColors = DaxColorDotColors(
                fillColor = DuckDuckGoTheme.colors.brand.accentBlue,
                strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
            ),
        )
    }
}
