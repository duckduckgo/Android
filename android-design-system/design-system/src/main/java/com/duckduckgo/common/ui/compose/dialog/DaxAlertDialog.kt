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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

/**
 * Base alert dialog composable for the DuckDuckGo design system.
 *
 * This is the foundation dialog that other specialized dialogs (TextAlertDialog,
 * StackedAlertDialog, RadioListAlertDialog) are built upon.
 *
 * @param onDismissRequest Callback invoked when the dialog should be dismissed
 * @param title The dialog title text (required)
 * @param modifier Modifier to be applied to the dialog content
 * @param message Optional message text displayed below the title
 * @param headerImage Optional drawable resource ID for an image displayed above the title
 * @param dismissOnClickOutside Whether clicking outside the dialog dismisses it (default: true)
 * @param content Optional composable slot for custom content between message and buttons
 * @param buttons Composable slot for dialog buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    dismissOnClickOutside: Boolean = true,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = {
            if (dismissOnClickOutside) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DaxAlertDialogContent(
            title = title,
            modifier = modifier.fillMaxWidth(0.83f),
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
    message: String? = null,
    @DrawableRes headerImage: Int? = null,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = DuckDuckGoTheme.shapes.medium,
        color = DuckDuckGoTheme.colors.backgrounds.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header image
            if (headerImage != null) {
                Image(
                    painter = painterResource(id = headerImage),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(40.dp),
                )
            }

            // Title
            DaxText(
                text = title,
                style = DuckDuckGoTheme.typography.h2,
                modifier = Modifier.fillMaxWidth(),
            )

            // Message
            if (message != null) {
                DaxText(
                    text = message,
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            // Custom content slot
            if (content != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    content()
                }
            }

            // Buttons slot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buttons()
            }
        }
    }
}
