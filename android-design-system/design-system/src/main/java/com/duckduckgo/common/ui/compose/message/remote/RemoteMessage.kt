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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.message.DaxMessage
import com.duckduckgo.common.ui.compose.message.DaxMessageButtonRowScope
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

/**
 * Internal base for remote message cards that include a top illustration.
 *
 * This is *not* part of the design system and should *only* be used when creating a *new*
 * design system compliant remote message variant. See [MediumMessage], [BigSingleActionMessage],
 * [BigTwoActionsMessage] for example usage.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param topIllustration Composable slot rendered above the title.
 * @param onDismissClicked Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 * @param bottomContent Composable slot rendered below the body — typically action buttons.
 */
@Composable
internal fun RemoteMessageWithIllustration(
    title: String,
    body: String,
    topIllustration: @Composable () -> Unit = {},
    onDismissClicked: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable DaxMessageButtonRowScope.() -> Unit = {},
) {
    RemoteMessage(
        title = title,
        body = body,
        onDismissClicked = onDismissClicked,
        modifier = modifier,
        topIllustration = topIllustration,
        bottomActions = bottomContent,
    )
}

/**
 * Internal base for remote message cards with a [Painter] illustration.
 *
 * This is *not* part of the design system and should *only* be used when creating a *new*
 * design system compliant remote message variant. The illustration is rendered at 48dp
 * with [ContentScale.Fit].
 *
 * @param title The message title.
 * @param body The message body text.
 * @param topIllustration The illustration drawn above the title.
 * @param onDismissClicked Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 * @param bottomContent Composable slot rendered below the body — typically action buttons.
 */
@Composable
internal fun RemoteMessageWithIllustration(
    title: String,
    body: String,
    topIllustration: Painter,
    onDismissClicked: () -> Unit,
    bottomContent: @Composable DaxMessageButtonRowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    RemoteMessage(
        title = title,
        body = body,
        onDismissClicked = onDismissClicked,
        modifier = modifier,
        topIllustration = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = topIllustration,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(
                            top = dimensionResource(R.dimen.keyline_3),
                            bottom = dimensionResource(R.dimen.keyline_2),
                        )
                        .align(Alignment.Center)
                        .size(48.dp),
                )
            }
        },
        bottomActions = bottomContent,
    )
}

/**
 * Internal base for remote message cards.
 *
 * This is *not* part of the design system and should *only* be used when creating a *new*
 * design system compliant remote message variant. Provides the shared card layout — title,
 * body, dismiss button, and slots for content above the title and below the body — used
 * by [SmallMessage] and the [RemoteMessageWithIllustration] variants.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param onDismissClicked Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 * @param topIllustration Composable slot rendered above the title — typically an illustration.
 * @param bottomActions Composable slot rendered below the body — typically action buttons.
 */
@Composable
internal fun RemoteMessage(
    title: String,
    body: String,
    onDismissClicked: () -> Unit,
    topIllustration: (@Composable () -> Unit)? = null,
    bottomActions: (@Composable DaxMessageButtonRowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DaxMessage(
        title = title,
        body = body,
        elevation = 4.dp,
        topContent = topIllustration?.let { { it() } },
        isDismissable = true,
        onDismiss = onDismissClicked,
        modifier = modifier.padding(dimensionResource(R.dimen.keyline_4)),
        buttonRow = bottomActions,
        shape = DuckDuckGoTheme.shapes.large,
    )
}
