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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.message.DaxAction
import com.duckduckgo.common.ui.compose.message.DaxMessageActions
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
 *   For remote URLs, always supply `error` and `fallback` painters so the card degrades
 *   gracefully when the request fails or the model is null:
 *
 *   ```
 *   topIllustration = rememberAsyncImagePainter(
 *       model = "https://...",
 *       error = painterResource(...),
 *       fallback = painterResource(...),
 *   )
 *   ```
 * @param primaryAction The primary call-to-action.
 * @param secondaryAction The secondary ghost action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214531387269892
 */
@Composable
fun DaxBigTwoActionsMessage(
    title: String,
    body: String,
    topIllustration: Painter,
    primaryAction: DaxAction,
    secondaryAction: DaxAction,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxRemoteMessageWithIllustration(
        title = title,
        body = body,
        topIllustration = topIllustration,
        modifier = modifier,
        onDismissClicked = onDismissed,
        actions = DaxMessageActions.CenterAligned(
            primary = primaryAction,
            secondary = secondaryAction,
        ),
    )
}

@PreviewLightDark
@Composable
private fun DaxBigTwoActionsMessagePreview() {
    PreviewBox {
        DaxBigTwoActionsMessage(
            title = "Big Two Actions Message",
            body = "Body text goes here. This component has two buttons and showcases an app update",
            topIllustration = painterResource(R.drawable.ic_app_update),
            primaryAction = DaxAction(text = "Action", onClick = {}),
            secondaryAction = DaxAction(text = "Secondary", onClick = {}),
            onDismissed = {},
            modifier = Modifier.padding(dimensionResource(R.dimen.keyline_4)),
        )
    }
}
