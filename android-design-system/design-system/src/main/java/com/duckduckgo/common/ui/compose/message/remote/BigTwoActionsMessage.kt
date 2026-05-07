/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.message.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.message.CenterAlignedButtons
import com.duckduckgo.common.ui.compose.message.DaxAction
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo big remote message card with primary and secondary actions.
 *
 * Variant with a top illustration, title, body, a primary action button paired with a
 * secondary ghost button, and a dismiss button.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param topIllustration The illustration shown above the title. Use [painterResource]
 *   for a local drawable, or `coil3.compose.rememberAsyncImagePainter` for a remote URL.
 * @param primaryActionText The label of the primary action button.
 * @param onPrimaryActionClick Called when the user taps the primary action.
 * @param secondaryActionText The label of the secondary (ghost) action button.
 * @param onSecondaryActionClick Called when the user taps the secondary action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
@Composable
fun BigTwoActionsMessage(
    title: String,
    body: String,
    topIllustration: Painter,
    primaryActionText: String,
    onPrimaryActionClick: () -> Unit,
    secondaryActionText: String,
    onSecondaryActionClick: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteMessageWithIllustration(
        title = title,
        body = body,
        topIllustration = topIllustration,
        modifier = modifier,
        onDismissClicked = onDismissed,
        bottomContent = {
            CenterAlignedButtons(
                primary = DaxAction(
                    text = primaryActionText,
                    onClick = onPrimaryActionClick,
                ),
                secondary = DaxAction(
                    text = secondaryActionText,
                    onClick = onSecondaryActionClick,
                ),
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun BigTwoActionsMessagePreview() {
    PreviewBox {
        BigTwoActionsMessage(
            title = "Big Two Actions Message",
            body = "Body text goes here. This component has two buttons and showcases and app update",
            topIllustration = painterResource(R.drawable.ic_app_update),
            primaryActionText = "Action",
            secondaryActionText = "Secondary",
            onPrimaryActionClick = {},
            onSecondaryActionClick = {},
            onDismissed = {},
        )
    }
}
