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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Card prompting the user to enable a notification-related feature.
 *
 * Built on top of [DaxMessage] with a start-aligned title and body, a right-aligned
 * primary/secondary action pair, and a rounded, elevated card surface.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param primaryAction The primary call-to-action (e.g. "Notify me").
 * @param secondaryAction The secondary ghost action (e.g. "Not now").
 * @param modifier Modifier for this message card.
 *
 * Asana Task: https://app.asana.com/1/137249556945/task/1214931438529738
 * Figma Reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=17708-27293&m=dev
 */
@Composable
fun DaxNotifyMe(
    title: String,
    body: String,
    primaryAction: DaxAction,
    secondaryAction: DaxAction,
    modifier: Modifier = Modifier,
) {
    DaxMessage(
        title = title,
        body = body,
        elevation = dimensionResource(R.dimen.keyline_1),
        shape = DuckDuckGoTheme.shapes.large,
        contentAlignment = DaxMessageContentAlignment.Start,
        modifier = modifier,
        actions = DaxMessageActions.RightAligned(
            primary = primaryAction,
            secondary = secondaryAction,
        ),
    )
}

@PreviewLightDark
@Composable
private fun DaxNotifyMePreview() {
    PreviewBox {
        DaxNotifyMe(
            title = "Keep an eye on blocked trackers",
            body = "Get updates about blocked tracking attempts in your notification drawer.",
            primaryAction = DaxAction(
                text = "Notify me",
                onClick = {},
            ),
            secondaryAction = DaxAction(
                text = "Not now",
                onClick = {},
            ),
            modifier = Modifier.padding(dimensionResource(R.dimen.keyline_4)),
        )
    }
}
