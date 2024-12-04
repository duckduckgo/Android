/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

object DuckDuckGoTheme {

    val colors: DuckDuckGoColors
        @Composable
        get() = LocalDuckDuckGoColors.current

    val shapes
        @Composable
        get() = LocalDuckDuckGoShapes.current

    val typography
        @Composable
        get() = LocalDuckDuckGoTypography.current
}

@Composable
fun ProvideDuckDuckGoTheme(
    colors: DuckDuckGoColors,
    shapes: Shapes = Shapes(),
    typography: DuckDuckGoTypography = DuckDuckGoTypography(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDuckDuckGoColors provides colors,
        LocalDuckDuckGoShapes provides shapes,
        LocalDuckDuckGoTypography provides typography,
        content = content,
    )
}

@Composable
fun DuckDuckGoTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) DarkColorPalette else LightColorPalette

    ProvideDuckDuckGoTheme(colors = colors, content = content)
}
