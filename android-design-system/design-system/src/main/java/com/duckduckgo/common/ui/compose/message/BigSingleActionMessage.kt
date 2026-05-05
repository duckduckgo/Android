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

package com.duckduckgo.common.ui.compose.message

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo big remote message card with a single primary action.
 *
 * Variant with a top illustration provided as a composable slot, title, body, one primary
 * action button, and a dismiss button. Use this overload when the illustration cannot be
 * expressed as a [Painter] (for example, a Lottie animation).
 *
 * For static images, prefer the [BigSingleActionMessage] overload that takes a [Painter].
 *
 * @param title The message title.
 * @param body The message body text.
 * @param topIllustration Composable slot rendered above the title. Receives a [ColumnScope]
 *   so the content can use `Modifier.align(...)` if needed.
 * @param actionText The label of the primary action button.
 * @param onActionClick Called when the user taps the primary action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
@Composable
fun BigSingleActionMessage(
    title: String,
    body: String,
    topIllustration: @Composable ColumnScope.() -> Unit,
    actionText: String,
    onActionClick: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteMessageWithIllustration(
        title = title,
        body = body,
        onDismissClicked = onDismissed,
        modifier = modifier,
        topIllustration = topIllustration,
        bottomContent = {
            DaxPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                size = DaxButtonSize.Small,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
            )
        },
    )
}

/**
 * DuckDuckGo big remote message card with a single primary action.
 *
 * Variant with a top illustration, title, body, one primary action button, and a dismiss
 * button.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param topIllustration The illustration shown above the title. Use [androidx.compose.ui.res.painterResource]
 *   for a local drawable, or `coil3.compose.rememberAsyncImagePainter` for a remote URL.
 * @param actionText The label of the primary action button.
 * @param onActionClick Called when the user taps the primary action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
@Composable
fun BigSingleActionMessage(
    title: String,
    body: String,
    topIllustration: Painter,
    actionText: String,
    onActionClick: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteMessageWithIllustration(
        title = title,
        body = body,
        modifier = modifier,
        topIllustration = topIllustration,
        onDismissClicked = onDismissed,
        bottomContent = {
            DaxPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                size = DaxButtonSize.Small,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun BigSingleActionMessagePreview() {
    PreviewBox {
        BigSingleActionMessage(
            title = "Big Single Message",
            body = "Body text goes here. This component has one button",
            topIllustration = painterResource(R.drawable.ic_ddg_announce),
            actionText = "Action",
            onActionClick = {},
            onDismissed = {},
        )
    }
}
