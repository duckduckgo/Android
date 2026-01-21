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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
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
 * A text-based alert dialog with optional checkbox and configurable buttons.
 *
 * @param onDismissRequest Callback invoked when the dialog should be dismissed
 * @param title The dialog title text (required)
 * @param positiveButton Text for the positive/confirm button (required)
 * @param onPositiveClick Callback invoked when the positive button is clicked
 * @param modifier Modifier to be applied to the dialog content
 * @param message Optional message text displayed below the title
 * @param headerImage Optional drawable resource ID for an image displayed above the title
 * @param positiveButtonType Style for the positive button (default: PRIMARY)
 * @param negativeButton Optional text for the negative/cancel button
 * @param onNegativeClick Optional callback invoked when the negative button is clicked
 * @param negativeButtonType Style for the negative button (default: GHOST)
 * @param checkboxText Optional text for a checkbox displayed above the buttons
 * @param checkboxChecked Current checked state of the checkbox
 * @param onCheckboxChanged Callback invoked when the checkbox state changes
 * @param dismissOnClickOutside Whether clicking outside the dialog dismisses it (default: true)
 */
@Composable
fun TextAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    positiveButton: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    positiveButtonType: DaxButtonType = DaxButtonType.PRIMARY,
    negativeButton: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    negativeButtonType: DaxButtonType = DaxButtonType.GHOST,
    checkboxText: String? = null,
    checkboxChecked: Boolean = false,
    onCheckboxChanged: ((Boolean) -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
) {
    DaxAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        message = message,
        headerImage = headerImage,
        dismissOnClickOutside = dismissOnClickOutside,
        content = if (checkboxText != null) {
            {
                TextAlertDialogCheckbox(
                    checkboxText = checkboxText,
                    checkboxChecked = checkboxChecked,
                    onCheckboxChanged = onCheckboxChanged,
                )
            }
        } else {
            null
        },
        buttons = {
            TextAlertDialogButtons(
                positiveButton = positiveButton,
                onPositiveClick = {
                    onPositiveClick()
                    onDismissRequest()
                },
                positiveButtonType = positiveButtonType,
                negativeButton = negativeButton,
                onNegativeClick = if (onNegativeClick != null) {
                    {
                        onNegativeClick()
                        onDismissRequest()
                    }
                } else {
                    null
                },
                negativeButtonType = negativeButtonType,
            )
        },
    )
}

/**
 * Content composable for [TextAlertDialog] that can be used in previews.
 * Dialogs don't render in Compose previews, so this allows previewing the dialog content.
 */
@Composable
internal fun TextAlertDialogContent(
    title: String,
    positiveButton: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    positiveButtonType: DaxButtonType = DaxButtonType.PRIMARY,
    negativeButton: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    negativeButtonType: DaxButtonType = DaxButtonType.GHOST,
    checkboxText: String? = null,
    checkboxChecked: Boolean = false,
    onCheckboxChanged: ((Boolean) -> Unit)? = null,
) {
    DaxAlertDialogContent(
        title = title,
        modifier = modifier,
        message = message,
        headerImage = headerImage,
        content = if (checkboxText != null) {
            {
                TextAlertDialogCheckbox(
                    checkboxText = checkboxText,
                    checkboxChecked = checkboxChecked,
                    onCheckboxChanged = onCheckboxChanged,
                )
            }
        } else {
            null
        },
        buttons = {
            TextAlertDialogButtons(
                positiveButton = positiveButton,
                onPositiveClick = onPositiveClick,
                positiveButtonType = positiveButtonType,
                negativeButton = negativeButton,
                onNegativeClick = onNegativeClick,
                negativeButtonType = negativeButtonType,
            )
        },
    )
}

@Composable
private fun TextAlertDialogCheckbox(
    checkboxText: String,
    checkboxChecked: Boolean,
    onCheckboxChanged: ((Boolean) -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Checkbox(
            checked = checkboxChecked,
            onCheckedChange = onCheckboxChanged,
            colors = CheckboxDefaults.colors(
                checkedColor = DuckDuckGoTheme.colors.brand.accentBlue,
                uncheckedColor = DuckDuckGoTheme.textColors.secondary,
                checkmarkColor = DuckDuckGoTheme.colors.icons.white,
            ),
        )
        DaxText(
            text = checkboxText,
            style = DuckDuckGoTheme.typography.body1,
            color = DuckDuckGoTheme.textColors.primary,
        )
    }
}

@Composable
private fun TextAlertDialogButtons(
    positiveButton: String,
    onPositiveClick: () -> Unit,
    positiveButtonType: DaxButtonType,
    negativeButton: String?,
    onNegativeClick: (() -> Unit)?,
    negativeButtonType: DaxButtonType,
) {
    Row {
        if (negativeButton != null && onNegativeClick != null) {
            DaxButton(
                text = negativeButton,
                buttonType = negativeButtonType,
                onClick = onNegativeClick,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        DaxButton(
            text = positiveButton,
            buttonType = positiveButtonType,
            onClick = onPositiveClick,
        )
    }
}

@PreviewLightDark
@Composable
private fun TextAlertDialogPreview() {
    PreviewBox {
        TextAlertDialogContent(
            title = "Dialog Title",
            message = "This is the dialog message explaining what's happening.",
            positiveButton = "Confirm",
            onPositiveClick = {},
            negativeButton = "Cancel",
            onNegativeClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TextAlertDialogWithCheckboxPreview() {
    PreviewBox {
        TextAlertDialogContent(
            title = "Remember Choice",
            message = "Would you like to save this preference?",
            positiveButton = "Save",
            onPositiveClick = {},
            negativeButton = "Cancel",
            onNegativeClick = {},
            checkboxText = "Don't ask again",
            checkboxChecked = true,
            onCheckboxChanged = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AlertDialogSingleButtonPreview() {
    PreviewBox {
        TextAlertDialogContent(
            title = "Information",
            message = "This is an informational dialog with only one button.",
            positiveButton = "OK",
            onPositiveClick = {},
        )
    }
}
