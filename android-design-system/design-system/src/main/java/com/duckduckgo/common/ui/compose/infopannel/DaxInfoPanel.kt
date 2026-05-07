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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun DaxInfoPanel(
    body: String,
    modifier: Modifier = Modifier
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

