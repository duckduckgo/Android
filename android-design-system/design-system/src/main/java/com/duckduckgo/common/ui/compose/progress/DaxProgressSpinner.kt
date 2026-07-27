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

package com.duckduckgo.common.ui.compose.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system indeterminate circular progress spinner.
 *
 * Wraps Material3 [CircularProgressIndicator] with DuckDuckGo theme colors.
 *
 * @param modifier the [Modifier] to apply.
 * @param strokeWidth the stroke width of the track and indicator. Defaults to 4dp (the Material3
 *   default at 40dp); reduce it when displaying the spinner at a smaller size.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1216871244926850?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6655-46405&m=dev
 */
@Composable
fun DaxProgressSpinner(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = DaxProgressSpinnerDefaults.StrokeWidth,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = DaxProgressSpinnerDefaults.HeadColor,
        trackColor = DaxProgressSpinnerDefaults.TrackColor,
        strokeWidth = strokeWidth,
        gapSize = DaxProgressSpinnerDefaults.TrackGapSize,
    )
}

private object DaxProgressSpinnerDefaults {
    val StrokeWidth: Dp = 4.dp
    val TrackGapSize: Dp = 0.dp

    val TrackColor: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.system.progressSpinnerTrack

    val HeadColor: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.system.progressSpinnerIndicator
}

@PreviewLightDark
@Composable
private fun DaxProgressSpinnerPreview() {
    PreviewBox {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DaxProgressSpinner()
            DaxProgressSpinner(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
