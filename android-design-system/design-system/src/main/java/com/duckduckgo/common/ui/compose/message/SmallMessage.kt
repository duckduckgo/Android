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
 * DuckDuckGo small remote message card.
 *
 * Compact variant with title, body, and a dismiss button. No illustration, no actions.
 *
 * @param title The message title.
 * @param body The message body text.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
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
