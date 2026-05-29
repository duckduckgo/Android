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

package com.duckduckgo.common.ui.compose.infopannel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * An alert panel used to surface warnings or information that needs the user's attention.
 *
 * Displays the given text alongside a warning icon on a yellow background. For neutral,
 * informational messages, use [DaxInfoPanel] instead.
 *
 * @param body The warning text to display in the panel.
 * @param modifier The [Modifier] to be applied to this panel.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215232366680610?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25970-57507&m=dev
 */
@Composable
fun DaxAlertPanel(
    body: String,
    modifier: Modifier = Modifier,
) {
    DaxAlertPanel(
        body = AnnotatedString(body),
        modifier = modifier,
    )
}

/**
 * [AnnotatedString] variant of [DaxAlertPanel]. Use this when the body needs inline styling or a
 * clickable link. Build the [AnnotatedString] with `buildAnnotatedString`, adding links via
 * `LinkAnnotation`.
 *
 * @param body The warning text to display in the panel, as an [AnnotatedString].
 * @param modifier The [Modifier] to be applied to this panel.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215232366680610?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25970-57507&m=dev
 */
@Composable
fun DaxAlertPanel(
    body: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    DaxPanel(
        body = body,
        modifier = modifier,
        icon = painterResource(R.drawable.ic_exclamation_yellow_16),
        color = DuckDuckGoTheme.colors.infoPanel.backgroundYellow,
    )
}

@PreviewLightDark
@Composable
private fun DaxAlertPanelPreview() {
    PreviewBox {
        DaxAlertPanel(
            body = "This is an Alert Info Panel, warning information can be shown here.",
        )
    }
}
