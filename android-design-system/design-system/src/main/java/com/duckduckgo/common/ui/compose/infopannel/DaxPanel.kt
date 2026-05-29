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
