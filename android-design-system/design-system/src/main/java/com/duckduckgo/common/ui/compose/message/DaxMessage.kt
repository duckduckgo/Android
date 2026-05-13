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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxCardElevation
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DaxMessage is the shared primitive used by the higher-level message components in this
 * package (banner, notify-me, and the remote message variants). It renders a card with a
 * title, body, optional content slot above the title, optional dismiss control, and an
 * optional typed action row from [DaxMessageActions].
 *
 * @param title The message title.
 * @param body The message body text.
 * @param modifier The [Modifier] to be applied to this message.
 * @param shape The [Shape] of the underlying card.
 * @param elevation The resting elevation of the underlying card.
 * @param contentAlignment Horizontal alignment of the title, body, and action row.
 * @param topContent Composable slot rendered above the title — typically an illustration.
 *   Receives a [ColumnScope] so the content can use `Modifier.align(...)` if needed.
 * @param actions Action row variant, restricted to the cases in [DaxMessageActions].
 *   `null` renders no action row.
 * @param onDismiss Optional dismiss callback. When non-null, a close button is rendered in
 *   the top-end corner and invokes this callback on tap.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1211724162604201/task/1211659112661253
 */
@Composable
internal fun DaxMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    shape: Shape = DaxMessageDefaults.shape,
    elevation: Dp = DaxMessageDefaults.elevation,
    contentAlignment: DaxMessageContentAlignment = DaxMessageContentAlignment.Center,
    topContent: (@Composable ColumnScope.() -> Unit)? = null,
    actions: DaxMessageActions? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val alignment = if (contentAlignment == DaxMessageContentAlignment.Start) {
        Alignment.Start
    } else {
        Alignment.CenterHorizontally
    }
    val textAlign = if (contentAlignment == DaxMessageContentAlignment.Start) {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment,
                ) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))
                    DaxText(
                        text = title,
                        style = DuckDuckGoTheme.typography.h3,
                        textAlign = textAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(alignment),
                    )
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_1)))
                    DaxText(
                        text = body,
                        style = DuckDuckGoTheme.typography.body2,
                        textAlign = textAlign,
                        modifier = Modifier.align(alignment),
                    )
                    if (actions != null) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_4)))
                        DaxMessageActionsRow(
                            actions = actions,
                            contentAlignment = contentAlignment,
                        )
                    }
                }
            }
            if (onDismiss != null) {
                DaxIconButton(
                    onClick = onDismiss,
                    iconPainter = painterResource(R.drawable.ic_close_24),
                    contentDescription = stringResource(R.string.closeButtonContentDescription),
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

enum class DaxMessageContentAlignment {
    Start,
    Center,
}

/**
 * Action row variants accepted by [DaxMessage].
 *
 * The sealed type restricts callers to the approved layouts in the same way an enum would,
 * but each variant carries its own action payload. If a future caller needs a layout not
 * listed here, open up a new variant rather than building one ad-hoc — that keeps every
 * Dax message visually consistent.
 */
sealed interface DaxMessageActions {
    /** Primary on the left, secondary ghost button on the right, packed to the start of the row. */
    data class RightAligned(val primary: DaxAction, val secondary: DaxAction) : DaxMessageActions

    /** Secondary ghost on the left, primary on the right, centered as a pair. */
    data class CenterAligned(val primary: DaxAction, val secondary: DaxAction) : DaxMessageActions

    /** A single primary button that fills the available width. */
    data class FullWidthSingle(val primary: DaxAction) : DaxMessageActions

    /** A single small primary button. */
    data class SmallSingle(val primary: DaxAction) : DaxMessageActions
}

/** A single button entry used by [DaxMessageActions]. */
data class DaxAction(
    val text: String,
    val onClick: () -> Unit,
)

private object DaxMessageDefaults {
    val shape: Shape = RoundedCornerShape(0.dp)
    val elevation: Dp = 0.dp
}

@Composable
private fun DaxMessageActionsRow(
    actions: DaxMessageActions,
    contentAlignment: DaxMessageContentAlignment,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when (contentAlignment) {
            DaxMessageContentAlignment.Start -> Alignment.CenterStart
            DaxMessageContentAlignment.Center -> Alignment.Center
        },
    ) {
        when (actions) {
            is DaxMessageActions.RightAligned -> RightAlignedActions(actions)
            is DaxMessageActions.CenterAligned -> CenterAlignedActions(actions)
            is DaxMessageActions.FullWidthSingle -> FullWidthSingleAction(actions)
            is DaxMessageActions.SmallSingle -> SmallSingleAction(actions)
        }
    }
}

@Composable
private fun RightAlignedActions(actions: DaxMessageActions.RightAligned) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        DaxPrimaryButton(
            text = actions.primary.text,
            onClick = actions.primary.onClick,
            size = DaxButtonSize.Small,
        )
        DaxGhostButton(
            text = actions.secondary.text,
            onClick = actions.secondary.onClick,
            size = DaxButtonSize.Small,
            modifier = Modifier.padding(start = dimensionResource(R.dimen.keyline_1)),
        )
    }
}

@Composable
private fun CenterAlignedActions(actions: DaxMessageActions.CenterAligned) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        DaxGhostButton(
            text = actions.secondary.text,
            onClick = actions.secondary.onClick,
            size = DaxButtonSize.Small,
        )
        DaxPrimaryButton(
            text = actions.primary.text,
            onClick = actions.primary.onClick,
            size = DaxButtonSize.Small,
            modifier = Modifier.padding(start = dimensionResource(R.dimen.keyline_1)),
        )
    }
}

@Composable
private fun FullWidthSingleAction(actions: DaxMessageActions.FullWidthSingle) {
    DaxPrimaryButton(
        text = actions.primary.text,
        onClick = actions.primary.onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SmallSingleAction(actions: DaxMessageActions.SmallSingle) {
    DaxPrimaryButton(
        text = actions.primary.text,
        size = DaxButtonSize.Small,
        onClick = actions.primary.onClick,
    )
}

@PreviewLightDark
@Composable
private fun DaxMessageStartPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. This component doesn't have buttons",
            contentAlignment = DaxMessageContentAlignment.Start,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageRightAlignedPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Primary + secondary right-aligned",
            contentAlignment = DaxMessageContentAlignment.Start,
            actions = DaxMessageActions.RightAligned(
                primary = DaxAction(text = "Primary", onClick = {}),
                secondary = DaxAction(text = "Secondary", onClick = {}),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageCenterAlignedPreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Primary + secondary centered",
            contentAlignment = DaxMessageContentAlignment.Center,
            actions = DaxMessageActions.CenterAligned(
                primary = DaxAction(text = "Primary", onClick = {}),
                secondary = DaxAction(text = "Secondary", onClick = {}),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageFullWidthSinglePreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Single full-width primary button",
            contentAlignment = DaxMessageContentAlignment.Center,
            actions = DaxMessageActions.FullWidthSingle(
                primary = DaxAction(text = "Primary", onClick = {}),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxMessageSmallSinglePreview() {
    PreviewBox {
        DaxMessage(
            title = "Dax message",
            body = "Body text goes here. Single small primary button",
            contentAlignment = DaxMessageContentAlignment.Center,
            actions = DaxMessageActions.SmallSingle(
                primary = DaxAction(text = "Primary", onClick = {}),
            ),
        )
    }
}
