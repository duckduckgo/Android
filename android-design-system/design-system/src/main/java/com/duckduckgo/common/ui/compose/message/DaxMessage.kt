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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxCardElevation
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun DaxMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    elevation: Dp = 0.dp,
    contentAlignment: ContentAlignment = ContentAlignment.Center,
    topContent: (@Composable () -> Unit)? = null,
    buttonRow: (@Composable DaxMessageButtonRowScope.() -> Unit)? = null,
    isDismissable: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val alignment = if (contentAlignment == ContentAlignment.Start) {
        Alignment.Start
    } else {
        Alignment.CenterHorizontally
    }
    val textAlign = if (contentAlignment == ContentAlignment.Start) {
        TextAlign.Start
    } else {
        TextAlign.Center
    }
    DaxCard(
        shape = shape,
        modifier = modifier,
        elevation = DaxCardElevation(elevation),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = dimensionResource(R.dimen.keyline_2),
                        start = dimensionResource(R.dimen.keyline_4),
                        end = dimensionResource(R.dimen.keyline_4),
                        bottom = dimensionResource(R.dimen.keyline_4),
                    ),
            ) {
                if (topContent != null) {
                    topContent()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = alignment,
                ) {
                    DaxText(
                        text = title,
                        style = DuckDuckGoTheme.typography.h3,
                        textAlign = textAlign,
                        modifier = Modifier
                            .padding(top = dimensionResource(R.dimen.keyline_2))
                            .fillMaxWidth()
                            .align(alignment),
                    )
                    DaxText(
                        text = body,
                        style = DuckDuckGoTheme.typography.body2,
                        textAlign = textAlign,
                        modifier = Modifier
                            .padding(top = dimensionResource(R.dimen.keyline_1))
                            .align(alignment),
                    )
                    if (buttonRow != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = dimensionResource(R.dimen.keyline_4)),
                            contentAlignment = when (contentAlignment) {
                                ContentAlignment.Start -> Alignment.CenterStart
                                ContentAlignment.Center -> Alignment.Center
                            },
                        ) {
                            DaxMessageButtonRowScopeImpl.buttonRow()
                        }
                    }
                }
            }
            if (isDismissable) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24),
                    contentDescription = stringResource(R.string.closeButtonContentDescription),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(dimensionResource(R.dimen.messageCtaCloseButtonSize))
                        .clip(CircleShape)
                        .clickable(
                            onClick = onDismiss,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                        )
                        .padding(dimensionResource(R.dimen.keyline_2)),
                )
            }
        }
    }
}

enum class ContentAlignment {
    Start,
    Center,
}

/**
 * Receiver scope for the [DaxMessage] `bottomActions` slot. Only the helpers defined as
 * extensions on this scope ([RightAlignButtons], [CenterAlignedButtons], [FullWidthSingleButton]) are intended to be
 * called inside the slot — restricts the action layouts to the three approved variants.
 */
interface DaxMessageButtonRowScope

internal object DaxMessageButtonRowScopeImpl : DaxMessageButtonRowScope

/** A single button entry passed to a [DaxMessageButtonRowScope] helper. */
data class DaxAction(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * Right-aligned actions: primary on the left, secondary text-style button on the right.
 */
@Composable
fun DaxMessageButtonRowScope.RightAlignButtons(
    primary: DaxAction,
    secondary: DaxAction,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        DaxPrimaryButton(
            text = primary.text,
            onClick = primary.onClick,
            size = DaxButtonSize.Small,
        )
        DaxGhostButton(
            text = secondary.text,
            onClick = secondary.onClick,
            size = DaxButtonSize.Small,
            modifier = Modifier.padding(start = dimensionResource(R.dimen.keyline_1)),
        )
    }
}

/**
 * Center-aligned actions: secondary text-style button on the left, primary on the right.
 */
@Composable
fun DaxMessageButtonRowScope.CenterAlignedButtons(
    primary: DaxAction,
    secondary: DaxAction,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        DaxGhostButton(
            text = secondary.text,
            onClick = secondary.onClick,
            size = DaxButtonSize.Small,
        )
        DaxPrimaryButton(
            text = primary.text,
            onClick = primary.onClick,
            size = DaxButtonSize.Small,
            modifier = Modifier.padding(start = dimensionResource(R.dimen.keyline_1)),
        )
    }
}

/**
 * Full-width single primary action.
 */
@Composable
fun DaxMessageButtonRowScope.FullWidthSingleButton(
    primary: DaxAction,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DaxPrimaryButton(
            text = primary.text,
            onClick = primary.onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Single primary, small button action.
 */
@Composable
fun DaxMessageButtonRowScope.SmallSingleButton(
    primary: DaxAction,
    modifier: Modifier = Modifier,
) {
    DaxPrimaryButton(
        text = primary.text,
        size = DaxButtonSize.Small,
        onClick = primary.onClick,
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DaxMessageStartPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. This component doesn't have buttons",
            contentAlignment = ContentAlignment.Start,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageRightAlignButtonsPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Primary + secondary right-aligned",
            contentAlignment = ContentAlignment.Start,
            buttonRow = {
                RightAlignButtons(
                    primary = DaxAction(text = "Primary", onClick = {}),
                    secondary = DaxAction(text = "Secondary", onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageCenterAlignedButtonsPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Primary + secondary centered",
            contentAlignment = ContentAlignment.Center,
            buttonRow = {
                CenterAlignedButtons(
                    primary = DaxAction(text = "Primary", onClick = {}),
                    secondary = DaxAction(text = "Secondary", onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageFullWidthSingleButtonPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Single full-width primary button",
            contentAlignment = ContentAlignment.Center,
            buttonRow = {
                FullWidthSingleButton(
                    primary = DaxAction(text = "Primary", onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageSmallSingleButtonPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Single full-width primary button",
            contentAlignment = ContentAlignment.Center,
            buttonRow = {
                SmallSingleButton(
                    primary = DaxAction(text = "Primary", onClick = {}),
                )
            },
        )
    }
}
