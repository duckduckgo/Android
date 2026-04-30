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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.buttons.SmallGhostButton
import com.duckduckgo.common.ui.compose.buttons.SmallPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun SmallMessage(
    title: String,
    body: String,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteMessage(
        title = title,
        body = body,
        modifier = modifier,
        onDismissClicked = onDismissed,
    )
}

@Composable
fun MediumMessage(
    title: String,
    body: String,
    topIllustration: Painter,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteMessageWithIllustration(
        title = title,
        body = body,
        modifier = modifier,
        onDismissClicked = onDismissed,
        topIllustration = topIllustration,
    )
}

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
            SmallPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
            )
        },
    )
}

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
            SmallPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
            )
        },
    )
}

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
            Row(
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_1), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallGhostButton(
                    text = secondaryActionText,
                    onClick = onSecondaryActionClick,
                )
                SmallPrimaryButton(
                    text = primaryActionText,
                    onClick = onPrimaryActionClick,
                    modifier = Modifier
                        .padding(start = dimensionResource(R.dimen.keyline_2)),
                )
            }
        },
    )
}

@Composable
internal fun RemoteMessageWithIllustration(
    title: String,
    body: String,
    topIllustration: @Composable ColumnScope.() -> Unit = {},
    onDismissClicked: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) {
    RemoteMessage(
        title = title,
        body = body,
        onDismissClicked = onDismissClicked,
        modifier = modifier,
        topContent = topIllustration,
        bottomContent = bottomContent,
    )
}

@Composable
internal fun RemoteMessageWithIllustration(
    title: String,
    body: String,
    topIllustration: Painter,
    onDismissClicked: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) {
    RemoteMessage(
        title = title,
        body = body,
        onDismissClicked = onDismissClicked,
        modifier = modifier,
        topContent = {
            Image(
                painter = topIllustration,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(
                        top = dimensionResource(R.dimen.keyline_2),
                        bottom = dimensionResource(R.dimen.keyline_2),
                    )
                    .size(48.dp),
            )
        },
        bottomContent = bottomContent,
    )
}

@Composable
internal fun RemoteMessage(
    title: String,
    body: String,
    onDismissClicked: () -> Unit,
    modifier: Modifier = Modifier,
    topContent: @Composable ColumnScope.() -> Unit = {},
    bottomContent: @Composable ColumnScope.() -> Unit = {},
) {
    DaxCard(
        modifier = modifier.padding(dimensionResource(R.dimen.keyline_4)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = DuckDuckGoTheme.shapes.large,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.keyline_2)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topContent()
                DaxText(
                    text = title,
                    style = DuckDuckGoTheme.typography.h3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(
                            top = dimensionResource(R.dimen.keyline_2),
                            start = 40.dp,
                            end = 40.dp,
                        )
                        .fillMaxWidth(),
                )
                DaxText(
                    text = body,
                    style = DuckDuckGoTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(
                            top = dimensionResource(R.dimen.keyline_1),
                            bottom = dimensionResource(R.dimen.keyline_2),
                            start = dimensionResource(R.dimen.keyline_4),
                            end = dimensionResource(R.dimen.keyline_4),
                        )
                        .fillMaxWidth(),
                )
                bottomContent()
            }
            Icon(
                painter = painterResource(R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.closeButtonContentDescription),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(dimensionResource(R.dimen.messageCtaCloseButtonSize))
                    .clip(CircleShape)
                    .clickable(
                        onClick = onDismissClicked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .padding(dimensionResource(R.dimen.keyline_2)),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SmallMessagePreview() {
    PreviewBox {
        SmallMessage(
            title = "Small Message",
            body = "Body text goes here. This component doesn't have buttons",
            onDismissed = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MediumMessagePreview() {
    PreviewBox {
        MediumMessage(
            title = "Medium message",
            body = "Body text goes here. This component doesn't have buttons",
            topIllustration = painterResource(R.drawable.ic_critical_update),
            onDismissed = {},
        )
    }
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
