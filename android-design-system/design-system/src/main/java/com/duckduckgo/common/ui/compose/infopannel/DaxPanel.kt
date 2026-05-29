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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

/**
 * Base composable for the Dax info panel family. Renders a rounded, full-width panel with a
 * leading icon and a body of text.
 *
 * This is the shared implementation backing the public variants such as [DaxInfoPanel] and
 * [DaxAlertPanel]. Prefer one of those variants over calling this directly, as they provide the
 * correct icon and background colour for each use case.
 *
 * @param body The text to display in the panel.
 * @param color The background colour of the panel.
 * @param icon The leading [Painter] icon displayed at the start of the panel.
 * @param modifier The [Modifier] to be applied to this panel.
 */
@Composable
internal fun DaxPanel(
    body: String,
    color: Color,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = color,
                shape = DuckDuckGoTheme.shapes.small,
            )
            .padding(dimensionResource(R.dimen.keyline_4)),

    ) {
        Image(
            painter = icon,
            modifier = modifier
                .padding(top = dimensionResource(R.dimen.keyline_1))
                .size(dimensionResource(R.dimen.infoPanelIconSize)),
            contentDescription = null,
        )
        DaxText(
            text = body,
            modifier = Modifier
                .padding(start = dimensionResource(R.dimen.keyline_4)),
            style = DuckDuckGoTheme.typography.body2,
        )
    }
}
