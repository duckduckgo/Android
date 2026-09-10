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

package com.duckduckgo.common.ui.compose.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DaxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = DaxDropdownMenuDefault.containerColor,
    shadowElevation: Dp = DaxDropdownMenuDefault.shadowElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        anchor()
        DropdownMenu(
            expanded = expanded,
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            containerColor = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = shadowElevation,
            shape = RectangleShape,
            content = content,
        )
    }
}



object DaxDropdownMenuDefault {
    val containerColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.surface

    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.small

    val shadowElevation: Dp
        get() = 4.dp
}
