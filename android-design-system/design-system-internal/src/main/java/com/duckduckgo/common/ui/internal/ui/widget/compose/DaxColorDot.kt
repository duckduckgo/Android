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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
internal fun DaxColorDot(
    colors: DaxColorDotColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(color = colors.fillColor, shape = CircleShape)
            .border(width = 0.1.dp, color = colors.strokeColor, shape = CircleShape),
    )
}

internal data class DaxColorDotColors(
    val fillColor: Color,
    val strokeColor: Color,
)

@PreviewLightDark
@Composable
private fun DaxColorDotPreview() {
    PreviewBox {
        DaxColorDot(
            colors = DaxColorDotColors(
                fillColor = DuckDuckGoTheme.colors.brand.accentBlue,
                strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
            ),
        )
    }
}
