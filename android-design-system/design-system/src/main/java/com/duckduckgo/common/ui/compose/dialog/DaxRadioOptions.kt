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

package com.duckduckgo.common.ui.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Radio button list usable inside the [DaxAlertDialog] `content` slot.
 *
 * Selection is hoisted — the caller owns [selectedIndex] so it can drive button enablement
 * or any other UI state. Scrolls independently when the list exceeds 176dp.
 *
 * Typical usage:
 * ```
 * var selected by rememberSaveable { mutableIntStateOf(-1) }
 * DaxAlertDialog(
 *     onDismissRequest = onDismiss,
 *     title = "Theme",
 *     content = {
 *         DaxRadioOptions(
 *             optionTitles = optionTitles,
 *             selectedIndex = selected,
 *             onOptionSelected = { selected = it },
 *         )
 *     },
 *     buttons = {
 *         DaxGhostButton(text = "Cancel", onClick = onDismiss)
 *         DaxPrimaryButton(text = "Apply", enabled = selected >= 0, onClick = ...)
 *     },
 * )
 * ```
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735717504168
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun DaxRadioOptions(
    optionTitles: ImmutableList<String>,
    selectedIndex: Int,
    onOptionSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 176.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        optionTitles.forEachIndexed { index, optionTitle ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedIndex == index,
                        onClick = { onOptionSelected(index) },
                        role = Role.RadioButton,
                    )
                    .padding(12.dp),
            ) {
                // TODO: replace with DaxRadioButton once the ADS radio button is migrated to Compose.
                RadioButton(
                    selected = selectedIndex == index,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        unselectedColor = DuckDuckGoTheme.textColors.secondary,
                    ),
                )
                DaxText(
                    text = optionTitle,
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.primary,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxRadioOptionsInDialogPreview() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Select Theme",
            message = {
                DaxText(
                    text = "Choose your preferred theme for the app.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            content = {
                DaxRadioOptions(
                    optionTitles = persistentListOf("Light", "Dark", "System Default"),
                    selectedIndex = 2,
                    onOptionSelected = {},
                )
            },
            buttons = {
                DaxGhostButton(text = "Cancel", onClick = {})
                DaxPrimaryButton(text = "Apply", onClick = {})
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxRadioOptionsUnselectedPreview() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Select Theme",
            message = {
                DaxText(
                    text = "Nothing chosen yet — apply button stays disabled.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            content = {
                DaxRadioOptions(
                    optionTitles = persistentListOf("Light", "Dark", "System Default"),
                    selectedIndex = -1,
                    onOptionSelected = {},
                )
            },
            buttons = {
                DaxGhostButton(text = "Cancel", onClick = {})
                DaxPrimaryButton(text = "Apply", enabled = false, onClick = {})
            },
        )
    }
}
