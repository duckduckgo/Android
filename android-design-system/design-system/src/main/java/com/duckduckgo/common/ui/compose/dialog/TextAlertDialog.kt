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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Convenience wrapper around [DaxAlertDialog] for the most common dialog shape: title + optional
 * message + primary positive button + optional ghost negative button (and an optional checkbox).
 *
 * For anything richer — destructive buttons, custom button styles, radio lists, stacked buttons,
 * clickable message spans, arbitrary content — use [DaxAlertDialog] directly with the concrete
 * `Dax*Button` composables from `com.duckduckgo.common.ui.compose.button`.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735774211999
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun TextAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    negativeButtonText: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    checkboxText: String? = null,
    checkboxChecked: Boolean = false,
    onCheckboxChanged: ((Boolean) -> Unit)? = null,
    cancellable: Boolean = false,
) {
    DaxAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        message = message?.let {
            { DaxText(text = it, style = DuckDuckGoTheme.typography.body1, color = DuckDuckGoTheme.textColors.secondary) }
        },
        headerImage = headerImage,
        cancellable = cancellable,
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
                positiveButtonText = positiveButtonText,
                onPositiveClick = {
                    onPositiveClick()
                    onDismissRequest()
                },
                negativeButtonText = negativeButtonText,
                onNegativeClick = if (onNegativeClick != null) {
                    {
                        onNegativeClick()
                        onDismissRequest()
                    }
                } else {
                    null
                },
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
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    negativeButtonText: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    checkboxText: String? = null,
    checkboxChecked: Boolean = false,
    onCheckboxChanged: ((Boolean) -> Unit)? = null,
) {
    DaxAlertDialogContent(
        title = title,
        modifier = modifier,
        message = message?.let {
            { DaxText(text = it, style = DuckDuckGoTheme.typography.body1, color = DuckDuckGoTheme.textColors.secondary) }
        },
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
                positiveButtonText = positiveButtonText,
                onPositiveClick = onPositiveClick,
                negativeButtonText = negativeButtonText,
                onNegativeClick = onNegativeClick,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextAlertDialogButtons(
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    negativeButtonText: String?,
    onNegativeClick: (() -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (negativeButtonText != null && onNegativeClick != null) {
            DaxGhostButton(text = negativeButtonText, onClick = onNegativeClick)
        }
        DaxPrimaryButton(text = positiveButtonText, onClick = onPositiveClick)
    }
}

@PreviewLightDark
@Composable
private fun TextAlertDialogPreview() {
    PreviewBox {
        TextAlertDialogContent(
            title = "Dialog Title",
            message = "This is the dialog message explaining what's happening.",
            positiveButtonText = "Confirm",
            onPositiveClick = {},
            negativeButtonText = "Cancel",
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
            positiveButtonText = "Save",
            onPositiveClick = {},
            negativeButtonText = "Cancel",
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
            positiveButtonText = "OK",
            onPositiveClick = {},
        )
    }
}
