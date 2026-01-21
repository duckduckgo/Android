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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.buttons.DaxButton
import com.duckduckgo.common.ui.compose.buttons.DaxButtonType
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * An alert dialog with a list of radio button options for single selection.
 *
 * @param onDismissRequest Callback invoked when the dialog should be dismissed
 * @param title The dialog title text (required)
 * @param options List of option labels for the radio buttons (required)
 * @param positiveButton Text for the positive/confirm button (required)
 * @param onPositiveClick Callback invoked when the positive button is clicked, receives the selected index
 * @param negativeButton Text for the negative/cancel button (required)
 * @param onNegativeClick Callback invoked when the negative button is clicked
 * @param modifier Modifier to be applied to the dialog content
 * @param message Optional message text displayed below the title
 * @param selectedIndex Initially selected option index, or -1 for no selection
 * @param onOptionSelected Callback invoked when an option is selected, receives the option index
 * @param positiveButtonType Style for the positive button (default: PRIMARY)
 * @param negativeButtonType Style for the negative button (default: GHOST)
 * @param dismissOnClickOutside Whether clicking outside the dialog dismisses it (default: true)
 */
@Composable
fun RadioListAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    options: List<String>,
    positiveButton: String,
    onPositiveClick: (selectedIndex: Int) -> Unit,
    negativeButton: String,
    onNegativeClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    selectedIndex: Int = -1,
    onOptionSelected: (index: Int) -> Unit = {},
    positiveButtonType: DaxButtonType = DaxButtonType.PRIMARY,
    negativeButtonType: DaxButtonType = DaxButtonType.GHOST,
    dismissOnClickOutside: Boolean = true,
) {
    var currentSelection by remember { mutableIntStateOf(selectedIndex) }

    DaxAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        message = message,
        dismissOnClickOutside = dismissOnClickOutside,
        content = {
            RadioListOptions(
                options = options,
                currentSelection = currentSelection,
                onOptionSelected = { index ->
                    currentSelection = index
                    onOptionSelected(index)
                },
            )
        },
        buttons = {
            RadioListButtons(
                positiveButton = positiveButton,
                onPositiveClick = {
                    onPositiveClick(currentSelection)
                    onDismissRequest()
                },
                positiveButtonType = positiveButtonType,
                positiveEnabled = currentSelection >= 0,
                negativeButton = negativeButton,
                onNegativeClick = {
                    onNegativeClick()
                    onDismissRequest()
                },
                negativeButtonType = negativeButtonType,
            )
        },
    )
}

/**
 * Content composable for [RadioListAlertDialog] that can be used in previews.
 * Dialogs don't render in Compose previews, so this allows previewing the dialog content.
 */
@Composable
internal fun RadioListAlertDialogContent(
    title: String,
    options: List<String>,
    positiveButton: String,
    onPositiveClick: (selectedIndex: Int) -> Unit,
    negativeButton: String,
    onNegativeClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    selectedIndex: Int = -1,
    onOptionSelected: (index: Int) -> Unit = {},
    positiveButtonType: DaxButtonType = DaxButtonType.PRIMARY,
    negativeButtonType: DaxButtonType = DaxButtonType.GHOST,
) {
    var currentSelection by remember { mutableIntStateOf(selectedIndex) }

    DaxAlertDialogContent(
        title = title,
        modifier = modifier,
        message = message,
        content = {
            RadioListOptions(
                options = options,
                currentSelection = currentSelection,
                onOptionSelected = { index ->
                    currentSelection = index
                    onOptionSelected(index)
                },
            )
        },
        buttons = {
            RadioListButtons(
                positiveButton = positiveButton,
                onPositiveClick = { onPositiveClick(currentSelection) },
                positiveButtonType = positiveButtonType,
                positiveEnabled = currentSelection >= 0,
                negativeButton = negativeButton,
                onNegativeClick = onNegativeClick,
                negativeButtonType = negativeButtonType,
            )
        },
    )
}

@Composable
private fun RadioListOptions(
    options: List<String>,
    currentSelection: Int,
    onOptionSelected: (index: Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOptionSelected(index) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = currentSelection == index,
                    onClick = { onOptionSelected(index) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        unselectedColor = DuckDuckGoTheme.textColors.secondary,
                    ),
                )
                DaxText(
                    text = option,
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.primary,
                )
            }
        }
    }
}

@Composable
private fun RadioListButtons(
    positiveButton: String,
    onPositiveClick: () -> Unit,
    positiveButtonType: DaxButtonType,
    positiveEnabled: Boolean,
    negativeButton: String,
    onNegativeClick: () -> Unit,
    negativeButtonType: DaxButtonType,
) {
    Row {
        DaxButton(
            text = negativeButton,
            buttonType = negativeButtonType,
            onClick = onNegativeClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        DaxButton(
            text = positiveButton,
            buttonType = positiveButtonType,
            enabled = positiveEnabled,
            onClick = onPositiveClick,
        )
    }
}

@PreviewLightDark
@Composable
private fun RadioListAlertDialogPreview() {
    PreviewBox {
        RadioListAlertDialogContent(
            title = "Select Theme",
            message = "Choose your preferred theme for the app.",
            options = listOf("Light", "Dark", "System Default"),
            selectedIndex = 2,
            positiveButton = "Apply",
            onPositiveClick = {},
            negativeButton = "Cancel",
            onNegativeClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun RadioListAlertDialogNoSelectionPreview() {
    PreviewBox {
        RadioListAlertDialogContent(
            title = "Select Option",
            options = listOf("Option A", "Option B", "Option C"),
            positiveButton = "Confirm",
            onPositiveClick = {},
            negativeButton = "Cancel",
            onNegativeClick = {},
        )
    }
}
