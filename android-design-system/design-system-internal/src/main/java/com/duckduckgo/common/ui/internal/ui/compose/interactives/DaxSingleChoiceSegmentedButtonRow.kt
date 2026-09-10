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

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DaxSingleChoiceSegmentedButtonRow(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            val selected = option == selectedOption
            SegmentedButton(
                label = {
                    DaxText(
                        text = option,
                        style = DuckDuckGoTheme.typography.button,
                        color = if (selected) {
                            DuckDuckGoTheme.colors.text.primaryInverted
                        } else {
                            DuckDuckGoTheme.colors.text.primary
                        },
                    )
                },
                selected = selected,
                onClick = {
                    onOptionSelected.invoke(option)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContentColor = DuckDuckGoTheme.colors.text.primaryInverted,
                    activeBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                    activeContainerColor = DuckDuckGoTheme.colors.brand.accentBlue,
                    inactiveContentColor = DuckDuckGoTheme.colors.text.primary,
                    inactiveBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                    inactiveContainerColor = DuckDuckGoTheme.colors.backgrounds.background,
                ),
            )
        }
    }
}
