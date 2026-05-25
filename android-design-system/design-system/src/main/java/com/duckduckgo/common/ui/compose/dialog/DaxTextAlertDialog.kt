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

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * State for the optional checkbox row in [DaxTextAlertDialog].
 */
@Immutable
data class DaxTextAlertDialogCheckboxState(
    val text: String,
    val checked: Boolean = false,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
)

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
fun DaxTextAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    headerImage: Painter? = null,
    negativeButtonText: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    checkbox: DaxTextAlertDialogCheckboxState? = null,
    cancellable: Boolean = false,
) {
    DaxAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        message = messageSlot(message),
        headerImage = headerImage,
        cancellable = cancellable,
        content = checkbox?.let { state -> { DaxTextAlertDialogCheckboxRow(state) } },
        buttons = {
            DaxTextAlertDialogButtons(
                positiveButtonText = positiveButtonText,
                onPositiveClick = {
                    onPositiveClick()
                    onDismissRequest()
                },
                negativeButtonText = negativeButtonText,
                onNegativeClick = onNegativeClick?.let {
                    {
                        it()
                        onDismissRequest()
                    }
                },
            )
        },
    )
}

/**
 * Content composable for [DaxTextAlertDialog] that can be used in previews.
 * Dialogs don't render in Compose previews, so this allows previewing the dialog content.
 */
@Composable
internal fun DaxTextAlertDialogContent(
    title: String,
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    headerImage: Painter? = null,
    negativeButtonText: String? = null,
    onNegativeClick: (() -> Unit)? = null,
    checkbox: DaxTextAlertDialogCheckboxState? = null,
) {
    DaxAlertDialogContent(
        title = title,
        modifier = modifier,
        message = messageSlot(message),
        headerImage = headerImage,
        content = checkbox?.let { state -> { DaxTextAlertDialogCheckboxRow(state) } },
        buttons = {
            DaxTextAlertDialogButtons(
                positiveButtonText = positiveButtonText,
                onPositiveClick = onPositiveClick,
                negativeButtonText = negativeButtonText,
                onNegativeClick = onNegativeClick,
            )
        },
    )
}

private fun messageSlot(text: String?): (@Composable () -> Unit)? =
    text?.let { messageText -> { DefaultMessage(messageText) } }

@Composable
private fun DefaultMessage(text: String) {
    DaxText(
        text = text,
        style = DuckDuckGoTheme.typography.body1,
        color = DuckDuckGoTheme.textColors.secondary,
    )
}

@Composable
private fun DaxTextAlertDialogCheckboxRow(state: DaxTextAlertDialogCheckboxState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaxCheckbox(
            checked = state.checked,
            onCheckedChange = state.onCheckedChange,
        )
        DaxText(
            text = state.text,
            style = DuckDuckGoTheme.typography.body1,
            color = DuckDuckGoTheme.textColors.primary,
        )
    }
}

@Composable
private fun DaxTextAlertDialogButtons(
    positiveButtonText: String,
    onPositiveClick: () -> Unit,
    negativeButtonText: String?,
    onNegativeClick: (() -> Unit)?,
) {
    if (negativeButtonText != null && onNegativeClick != null) {
        DaxGhostButton(text = negativeButtonText, onClick = onNegativeClick)
    }
    DaxPrimaryButton(text = positiveButtonText, onClick = onPositiveClick)
}

@PreviewLightDark
@Composable
private fun DaxTextAlertDialogPreview() {
    PreviewBox {
        DaxTextAlertDialogContent(
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
private fun DaxTextAlertDialogWithCheckboxPreview() {
    PreviewBox {
        DaxTextAlertDialogContent(
            title = "Remember Choice",
            message = "Would you like to save this preference?",
            positiveButtonText = "Save",
            onPositiveClick = {},
            negativeButtonText = "Cancel",
            onNegativeClick = {},
            checkbox = DaxTextAlertDialogCheckboxState(
                text = "Don't ask again",
                checked = true,
                onCheckedChange = {},
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextAlertDialogSingleButtonPreview() {
    PreviewBox {
        DaxTextAlertDialogContent(
            title = "Information",
            message = "This is an informational dialog with only one button.",
            positiveButtonText = "OK",
            onPositiveClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTextAlertDialogWithImagePreview() {
    PreviewBox {
        DaxTextAlertDialogContent(
            title = "Dialog Title",
            message = "Header image rendered above the title at 24dp.",
            positiveButtonText = "Confirm",
            onPositiveClick = {},
            negativeButtonText = "Cancel",
            onNegativeClick = {},
            headerImage = painterResource(com.duckduckgo.mobile.android.R.drawable.ic_dax_icon),
        )
    }
}
