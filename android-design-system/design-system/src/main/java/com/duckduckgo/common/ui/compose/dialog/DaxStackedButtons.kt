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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostAltButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostButton
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class DaxStackedButton(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * Vertically-stacked buttons with a destructive action at the top, usable inside the
 * [DaxAlertDialog] `buttons` slot.
 *
 * - The first button renders as [DaxDestructiveGhostButton] (the destructive action).
 * - All other buttons render as [DaxDestructiveGhostAltButton] (muted siblings).
 * - End-aligned.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735768823555
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun DaxDestructiveStackedButtons(
    buttons: ImmutableList<DaxStackedButton>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_2)),
    ) {
        buttons.forEachIndexed { index, button ->
            if (index == 0) {
                DaxDestructiveGhostButton(text = button.text, onClick = button.onClick)
            } else {
                DaxDestructiveGhostAltButton(text = button.text, onClick = button.onClick)
            }
        }
    }
}

/**
 * Vertically-stacked buttons with a primary action at the top, usable inside the
 * [DaxAlertDialog] `buttons` slot.
 *
 * - The first button renders as [DaxPrimaryButton] (the main action).
 * - All other buttons render as [DaxGhostButton] (secondary actions).
 * - End-aligned.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735768823555
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun DaxPrimaryStackedButtons(
    buttons: ImmutableList<DaxStackedButton>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_2)),
    ) {
        buttons.forEachIndexed { index, button ->
            if (index == 0) {
                DaxPrimaryButton(text = button.text, onClick = button.onClick)
            } else {
                DaxGhostButton(text = button.text, onClick = button.onClick)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxDestructiveStackedButtonsPreview() {
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
                DaxDestructiveStackedButtons(
                    buttons = persistentListOf(
                        DaxStackedButton(text = "Delete", onClick = {}),
                        DaxStackedButton(text = "Move to trash", onClick = {}),
                        DaxStackedButton(text = "Cancel", onClick = {}),
                    ),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPrimaryStackedButtonsPreview() {
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
                DaxPrimaryStackedButtons(
                    buttons = persistentListOf(
                        DaxStackedButton(text = "Continue", onClick = {}),
                        DaxStackedButton(text = "Option 2", onClick = {}),
                        DaxStackedButton(text = "Cancel", onClick = {}),
                    ),
                )
            },
        )
    }
}
