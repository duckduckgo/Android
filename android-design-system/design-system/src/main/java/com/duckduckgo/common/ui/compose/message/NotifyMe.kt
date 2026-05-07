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
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun NotifyMe(
    title: String,
    body: String,
    primaryAction: DaxAction,
    secondaryAction: DaxAction,
    modifier: Modifier = Modifier,
) {
    DaxMessage(
        title = title,
        body = body,
        elevation = 4.dp,
        shape = DuckDuckGoTheme.shapes.large,
        contentAlignment = ContentAlignment.Start,
        modifier = modifier.padding(dimensionResource(R.dimen.keyline_4)),
        buttonRow = {
            RightAlignButtons(
                primary = primaryAction,
                secondary = secondaryAction,
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun DaxBannerMessagePreview() {
    PreviewBox {
        NotifyMe(
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
        )
    }
}
