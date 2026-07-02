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

package com.duckduckgo.common.ui.compose.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.common.ui.compose.theme.Black
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Yellow50
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * A reusable yellow pill for short status labels (e.g. "Beta", "New").
 *
 * Fixed palette in both light and dark (DuckDuckGo yellow background, black text) — it is not
 * theme-tokened and draws its own text colour, matching the View [DaxYellowPill]. Callers supply
 * their own (localised) label.
 *
 * @param text Pill label; rendered upper-cased.
 * @param modifier Modifier applied to the pill container.
 */
@Composable
fun DaxPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(DaxPillDefaults.Height)
            .clip(RoundedCornerShape(DaxPillDefaults.CornerRadius))
            .background(Yellow50)
            .padding(horizontal = DaxPillDefaults.HorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = Black,
            style = DuckDuckGoTheme.typography.caption.asTextStyle.copy(
                fontSize = DaxPillDefaults.FontSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

internal object DaxPillDefaults {
    val Height = 15.dp
    val CornerRadius = 2.dp
    val HorizontalPadding = 4.dp
    val FontSize = 10.sp
}

@PreviewLightDark
@Composable
private fun DaxPillPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DaxPill(text = "Beta")
            DaxPill(text = "New")
        }
    }
}
