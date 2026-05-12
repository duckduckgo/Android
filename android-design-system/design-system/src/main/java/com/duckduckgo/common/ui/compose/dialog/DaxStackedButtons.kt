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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostAltButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostButton
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Vertically-stacked buttons usable inside the [DaxAlertDialog] `buttons` slot.
 *
 * - All buttons render as ghost.
 * - When [destructiveButtonIndex] is non-null, that button uses the destructive ghost style and
 *   the rest use the muted alt-ghost style.
 * - End-aligned.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735768823555
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun DaxStackedButtons(
    buttonTitles: ImmutableList<String>,
    onButtonClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    destructiveButtonIndex: Int? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        buttonTitles.forEachIndexed { index, buttonTitle ->
            val onClick = { onButtonClick(index) }
            when {
                destructiveButtonIndex == index -> DaxDestructiveGhostButton(text = buttonTitle, onClick = onClick)
                destructiveButtonIndex != null -> DaxDestructiveGhostAltButton(text = buttonTitle, onClick = onClick)
                else -> DaxGhostButton(text = buttonTitle, onClick = onClick)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxStackedButtonsInDialogPreview() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Choose an Option",
            message = {
                DaxText(
                    text = "Select one of the options below to continue.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            buttons = {
                DaxStackedButtons(
                    buttonTitles = persistentListOf("Option 1", "Option 2", "Option 3"),
                    onButtonClick = {},
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxStackedButtonsDestructivePreview() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Delete Item?",
            message = {
                DaxText(
                    text = "This action cannot be undone.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            buttons = {
                DaxStackedButtons(
                    buttonTitles = persistentListOf("Keep", "Delete"),
                    onButtonClick = {},
                    destructiveButtonIndex = 1,
                )
            },
        )
    }
}
