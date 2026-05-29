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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Inline banner message with a primary and secondary action.
 *
 * Built on top of [DaxMessage] with a start-aligned title and body and a right-aligned
 * primary/secondary action pair. Has no elevation or rounded corners by default — intended
 * to be embedded inline within a screen.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param primaryAction The primary call-to-action.
 * @param secondaryAction The secondary ghost action.
 * @param modifier Modifier for this message card.
 *
 * Asana Task: https://app.asana.com/1/137249556945/task/1214931438529739
 * Figma Reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=14379-111153&m=dev
 */
@Composable
fun DaxBannerMessage(
    title: String,
    body: String,
    primaryAction: DaxAction,
    secondaryAction: DaxAction,
    modifier: Modifier = Modifier,
) {
    DaxMessage(
        title = title,
        body = body,
        contentAlignment = DaxMessageContentAlignment.Start,
        actions = DaxMessageActions.RightAligned(
            primary = primaryAction,
            secondary = secondaryAction,
        ),
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DaxBannerMessagePreview() {
    PreviewBox {
        DaxBannerMessage(
            title = "Site not working? Let us know.",
            body = "This helps improve our browser",
            primaryAction = DaxAction(
                text = "Website is Broken",
                onClick = {},
            ),
            secondaryAction = DaxAction(
                text = "Dismiss",
                onClick = {},
            ),
        )
    }
}
