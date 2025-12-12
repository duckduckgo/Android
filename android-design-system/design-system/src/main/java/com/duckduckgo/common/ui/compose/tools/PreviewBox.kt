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

package com.duckduckgo.common.ui.compose.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun PreviewBoxInverted(
    modifier: Modifier = Modifier,
    color: @Composable () -> Color = { DuckDuckGoTheme.colors.backgrounds.backgroundInverted },
    content: @Composable BoxScope.() -> Unit,
) = PreviewBox(
    modifier = modifier,
    color = color,
    content = content,
)

@Composable
fun PreviewBox(
    modifier: Modifier = Modifier,
    color: @Composable () -> Color = { DuckDuckGoTheme.colors.backgrounds.background },
    content: @Composable BoxScope.() -> Unit,
) {
    DuckDuckGoTheme {
        Box(
            modifier = modifier
                .background(color())
                .padding(all = 16.dp),
            content = content,
        )
    }
}
