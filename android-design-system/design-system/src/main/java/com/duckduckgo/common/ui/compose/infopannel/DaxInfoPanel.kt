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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * An informational panel used to surface important, non-critical information to the user.
 *
 * Displays the given text alongside an info icon on a blue background. For warnings or alerts,
 * use [DaxAlertPanel] instead.
 *
 * @param body The informational text to display in the panel.
 * @param modifier The [Modifier] to be applied to this panel.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215232366615036?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25970-57493&m=dev
 */
@Composable
fun DaxInfoPanel(
    body: String,
    modifier: Modifier = Modifier,
) {
    DaxInfoPanel(
        body = AnnotatedString(body),
        modifier = modifier,
    )
}

/**
 * [AnnotatedString] variant of [DaxInfoPanel]. Use this when the body needs inline styling or a
 * clickable link. Build the [AnnotatedString] with `buildAnnotatedString`, adding links via
 * `LinkAnnotation`.
 *
 * @param body The informational text to display in the panel, as an [AnnotatedString].
 * @param modifier The [Modifier] to be applied to this panel.
 *
 * Asana task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215232366615036?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=25970-57493&m=dev
 */
@Composable
fun DaxInfoPanel(
    body: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    DaxPanel(
        body = body,
        modifier = modifier,
        icon = painterResource(R.drawable.ic_info_panel_info),
        color = DuckDuckGoTheme.colors.infoPanel.backgroundBlue,
    )
}

@PreviewLightDark
@Composable
private fun DaxInfoPanelPreview() {
    PreviewBox {
        DaxAlertPanel(
            body = "This is an info panel. It can be used to show important information to the user.",
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxInfoPanelWithLinkPreview() {
    PreviewBox {
        DaxInfoPanel(
            body = buildAnnotatedString {
                append("This info panel has a link. Visit ")
                withLink(LinkAnnotation.Url("https://duckduckgo.com")) {
                    append("duckduckgo.com")
                }
                append(" to learn more.")
            },
        )
    }
}
