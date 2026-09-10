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

package com.duckduckgo.common.ui.internal.ui.compose.interactives

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DaxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = DuckDuckGoTheme.colors.brand.accentBlue,
            activeTrackColor = DuckDuckGoTheme.colors.brand.accentBlue,
            activeTickColor = DuckDuckGoTheme.colors.brand.accentBlue,
            inactiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand50,
            inactiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand50,
            disabledThumbColor = DuckDuckGoTheme.colors.brand.accentBrand50,
            disabledInactiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand20,
            disabledInactiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand20,
            disabledActiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand50,
            disabledActiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand50
        ),
    )
}
