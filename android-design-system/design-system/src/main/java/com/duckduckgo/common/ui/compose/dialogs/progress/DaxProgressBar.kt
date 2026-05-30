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

package com.duckduckgo.common.ui.compose.dialogs.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
internal fun DotProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DaxProgressBarDefault.dotGapSpacing),
    ) {
        for (index in 1..total) {
            val isCurrent = index == current
            val isComplete = index < current

            Dot(
                color = when {
                    isCurrent -> DaxProgressBarDefault.dotCompleteColor
                    isComplete -> DaxProgressBarDefault.dotCompleteColor
                    else -> DaxProgressBarDefault.dotIncompleteColor
                },
                size = if (isCurrent) DaxProgressBarDefault.dotCurrentSize else DaxProgressBarDefault.dotOtherSize,
            )
        }
    }
}

@Composable
private fun Dot(
    modifier: Modifier = Modifier,
    color: Color = DaxProgressBarDefault.dotCompleteColor,
    size: Dp = DaxProgressBarDefault.dotCurrentSize,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

internal object DaxProgressBarDefault {
    val contentPadding: PaddingValues
        @Composable
        get() = PaddingValues(top = 6.dp, bottom = 6.dp, start = 8.dp, end = 10.dp)

    val dotCompleteColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.brand.accentBrand50

    val dotIncompleteColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.brand.accentBrand20

    val border: BorderStroke
        @Composable
        get() = BorderStroke(width = 1.5.dp, color = DuckDuckGoTheme.colors.brand.accentBrand20)

    val textStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body2

    val dotCurrentSize: Dp = 12.dp
    val dotOtherSize: Dp = 8.dp
    val dotGapSpacing: Dp = 4.dp
    val minWidthOf3: Dp = 96.dp
    val minWidthOf5: Dp = 112.dp
}
