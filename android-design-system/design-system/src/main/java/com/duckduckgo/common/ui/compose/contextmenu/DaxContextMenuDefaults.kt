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

package com.duckduckgo.common.ui.compose.contextmenu

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

internal object DaxContextMenuDefaults {
    val Offset: DpOffset = DpOffset(0.dp, 0.dp)

    val MinWidth: Dp = 240.dp

    val ContainerElevation: Dp = 4.dp

    val TonalElevation: Dp = 0.dp

    val Shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.small

    val ContainerColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.window
}
