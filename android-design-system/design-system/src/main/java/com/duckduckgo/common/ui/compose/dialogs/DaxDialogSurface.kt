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

package com.duckduckgo.common.ui.compose.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.dialogs.wave.DynamicWaveShape
import com.duckduckgo.common.ui.compose.dialogs.wave.WaveEdge
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
internal fun DaxDialogSurface(
    modifier: Modifier = Modifier,
    edge: WaveEdge = WaveEdge.Bottom,
    wavePosition: Float = 0.5f,
    waveBaseWidth: Dp = 40.dp,
    waveAmplitude: Dp = 30.dp,
    content: @Composable BoxScope.() -> Unit
) {
    // Dynamically pad the inner content so it respects the wave's space
    val wavePadding = remember(edge, waveAmplitude) {
        PaddingValues(
            top = if (edge == WaveEdge.Top) waveAmplitude else 0.dp,
            bottom = if (edge == WaveEdge.Bottom) waveAmplitude else 0.dp,
            start = if (edge == WaveEdge.Left) waveAmplitude else 0.dp,
            end = if (edge == WaveEdge.Right) waveAmplitude else 0.dp,
        )
    }
    val currentShape = DynamicWaveShape(
        edge = edge,
        position = wavePosition,
        waveBaseWidthDp = waveBaseWidth,
        waveAmplitudeDp = waveAmplitude,
        cornerRadiusDp = DaxDialogSurfaceDefaults.cornerRadius,
    )
    Surface(
        modifier = modifier,
        shape = currentShape,
        color = DaxDialogSurfaceDefaults.backgroundColor,
        border = DaxDialogSurfaceDefaults.border,
        shadowElevation = DaxDialogSurfaceDefaults.shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .padding(wavePadding)
                .padding(DaxDialogSurfaceDefaults.contentPadding),
            content = content,
        )
    }
}

private object DaxDialogSurfaceDefaults {
    val contentPadding: PaddingValues = PaddingValues(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)

    val backgroundColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.window

    val border: BorderStroke
        @Composable
        get() = BorderStroke(1.5.dp, DuckDuckGoTheme.colors.brand.accentBrand20)

    val shadowElevation: Dp = 12.dp

    val cornerRadius: Dp = 36.dp
}

