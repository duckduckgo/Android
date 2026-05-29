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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.duckduckgo.common.ui.compose.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Base alert dialog composable for the DuckDuckGo design system.
 *
 * Slot-based: use this directly for any dialog. The convenience wrapper [DaxTextAlertDialog]
 * exists for the common title/message/positive/negative shape. For richer dialogs, use
 * this composable directly with helpers like [DaxRadioOptions] (content slot) or
 * [DaxPrimaryStackedButtons] / [DaxDestructiveStackedButtons] (buttons slot).
 *
 * Buttons are pinned at the bottom and never scroll. The [content] slot is responsible for
 * its own scrolling when needed (see [DaxRadioOptions]).
 *
 * @param onDismissRequest Callback invoked when the dialog should be dismissed
 * @param title The dialog title text (required)
 * @param modifier Modifier applied to the dialog surface
 * @param message Optional message slot rendered below the title. Use a plain [DaxText] for
 *  static text or pass any composable (e.g. an annotated [androidx.compose.material3.Text]
 *  with link annotations) for clickable spans.
 * @param headerImage Optional [Painter] for an image displayed above the title
 * @param cancellable Whether the dialog can be dismissed by clicking outside or pressing back (default: false)
 * @param content Optional composable slot for custom content between message and buttons
 * @param buttons Composable slot for dialog buttons (pinned, won't scroll)
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214735717450949
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=685-956&t=DvV3Fi7Mi45nLle2-4
 */
@Composable
fun DaxAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: (@Composable () -> Unit)? = null,
    headerImage: Painter? = null,
    cancellable: Boolean = false,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val dialogWidth = (screenWidthDp * 0.79f).coerceIn(minimumValue = 280.dp, maximumValue = 560.dp)

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = cancellable,
            dismissOnBackPress = cancellable,
        ),
    ) {
        DaxAlertDialogContent(
            title = title,
            modifier = modifier.width(dialogWidth),
            message = message,
            headerImage = headerImage,
            content = content,
            buttons = buttons,
        )
    }
}

/**
 * The content of [DaxAlertDialog] extracted for preview support.
 * Dialogs don't render in Compose previews, so this allows previewing the dialog content.
 */
@Composable
internal fun DaxAlertDialogContent(
    title: String,
    modifier: Modifier = Modifier,
    message: (@Composable () -> Unit)? = null,
    headerImage: Painter? = null,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    DaxSurface(
        modifier = modifier,
        shape = DuckDuckGoTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.keyline_5)),
        ) {
            if (headerImage != null) {
                Image(
                    painter = headerImage,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(24.dp),
                )
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_4)))
            }

            DaxText(
                text = title,
                style = DuckDuckGoTheme.typography.h2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))

            if (message != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    message()
                }
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))
            }

            if (content != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_2), Alignment.End),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_2)),
            ) {
                buttons()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxAlertDialogAllSlotsPreview() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Dialog Title",
            message = {
                DaxText(
                    text = "This dialog uses every slot the base composable exposes.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            headerImage = painterResource(R.drawable.ic_dax_icon),
            buttons = {
                DaxGhostButton(text = "Cancel", onClick = {})
                DaxPrimaryButton(text = "Confirm", onClick = {})
            },
        )
    }
}
