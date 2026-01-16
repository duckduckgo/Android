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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.buttons.DaxButton
import com.duckduckgo.common.ui.compose.buttons.DaxButtonType
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * An alert dialog with vertically stacked buttons.
 *
 * @param onDismissRequest Callback invoked when the dialog should be dismissed
 * @param title The dialog title text (required)
 * @param buttons List of button labels to display vertically stacked
 * @param onButtonClick Callback invoked when a button is clicked, receives the button index
 * @param modifier Modifier to be applied to the dialog content
 * @param message Optional message text displayed below the title
 * @param headerImage Optional drawable resource ID for an image displayed above the title
 * @param destructiveButtonIndex Optional index of a button that should be styled as destructive.
 *        When set, other buttons will use secondary text color.
 */
@Composable
fun StackedAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    buttons: List<String>,
    onButtonClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    destructiveButtonIndex: Int? = null,
) {
    DaxAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        message = message,
        headerImage = headerImage,
        dismissOnClickOutside = false,
        buttons = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                buttons.forEachIndexed { index, buttonText ->
                    val buttonType = when {
                        destructiveButtonIndex == index -> DaxButtonType.GHOST_DESTRUCTIVE
                        destructiveButtonIndex != null -> DaxButtonType.SECONDARY
                        else -> DaxButtonType.GHOST
                    }

                    DaxButton(
                        text = buttonText,
                        buttonType = buttonType,
                        onClick = {
                            onButtonClick(index)
                            onDismissRequest()
                        },
                    )
                }
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun StackedAlertDialogPreview() {
    PreviewBox {
        StackedAlertDialog(
            onDismissRequest = {},
            title = "Choose an Option",
            message = "Select one of the options below to continue.",
            buttons = listOf("Option 1", "Option 2", "Option 3"),
            onButtonClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun StackedAlertDialogDestructivePreview() {
    PreviewBox {
        StackedAlertDialog(
            onDismissRequest = {},
            title = "Delete Item?",
            message = "This action cannot be undone.",
            buttons = listOf("Keep", "Delete"),
            onButtonClick = {},
            destructiveButtonIndex = 1,
        )
    }
}
