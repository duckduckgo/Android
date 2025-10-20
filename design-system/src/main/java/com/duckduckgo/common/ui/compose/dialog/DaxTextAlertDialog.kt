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

import android.R.attr.text
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.InvertedPreviewBox
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
fun TextAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    @DrawableRes image: Int? = null,
) {
    AlertDialog(
        title = {
            DaxText(
                text = dialogTitle,
                style = DuckDuckGoTheme.typography.h2,
            )
        },
        text = {
            Row {
                RadioButton(
                    selected = true,
                    onClick = null, // null recommended for accessibility with screen readers
                )
                RadioButton(
                    selected = false,
                    onClick = null, // null recommended for accessibility with screen readers
                )
                // DaxText(
                //     text = dialogText,
                //     color = DuckDuckGoTheme.textColors.secondary,
                // )
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                },
            ) {
                DaxText("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                },
            ) {
                DaxText("Dismiss")
            }
        },
        containerColor = DuckDuckGoTheme.colors.surface,
        shape = DuckDuckGoTheme.shapes.medium,
    )
}

@PreviewLightDark
@Composable
private fun AlertDialogPreview() {
    PreviewBox {
        TextAlertDialog(
            onDismissRequest = {},
            onConfirmation = {},
            dialogTitle = "Preview Title",
            dialogText = "This is a preview of the AlertDialog.",
        )
    }
}

@PreviewLightDark
@Composable
private fun AlertDialogInvertedPreview() {
    InvertedPreviewBox {
        TextAlertDialog(
            onDismissRequest = {},
            onConfirmation = {},
            dialogTitle = "Preview Title",
            dialogText = "This is a preview of the AlertDialog.",
        )
    }
}
