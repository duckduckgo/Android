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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.duckduckgo.common.ui.compose.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo design system composable slider component.
 *
 * Wraps Material3 [Slider] with DuckDuckGo theme colors. Value labels, tick marks, stop indicators
 * and the thumb press halo are not drawn.
 *
 * @param value the current value of the slider, coerced into [valueRange]
 * @param onValueChange callback invoked continuously as the slider is dragged
 * @param modifier the [Modifier] to apply
 * @param enabled whether the slider is enabled
 * @param valueRange the inclusive range of values this slider can take
 * @param steps the number of discrete values between the ends of [valueRange], or 0 for a continuous slider
 * @param onValueChangeFinished callback invoked when the drag gesture ends, for committing the settled value
 * @param interactionSource the [MutableInteractionSource] representing the stream of interactions for this slider
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1218311474562583?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=3116-4290&m=dev
 */
@Composable
fun DaxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = DaxSliderDefaults.colors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else DaxSliderDefaults.DisabledAlpha),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = colors,
        thumb = {
            Box(
                modifier = Modifier
                    .size(DaxSliderDefaults.ThumbSize)
                    .clip(CircleShape)
                    .background(DuckDuckGoTheme.colors.brand.accentBlue),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(DaxSliderDefaults.TrackHeight),
                enabled = enabled,
                colors = colors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
                thumbTrackGapSize = 0.dp,
            )
        },
    )
}

private object DaxSliderDefaults {

    const val DisabledAlpha = 0.4f

    val ThumbSize = 20.dp

    val TrackHeight: Dp
        @Composable
        get() = dimensionResource(R.dimen.sliderTrackHeight)

    @Composable
    fun colors(): SliderColors = SliderDefaults.colors(
        thumbColor = DuckDuckGoTheme.colors.brand.accentBlue,
        activeTrackColor = DuckDuckGoTheme.colors.brand.accentBlue,
        inactiveTrackColor = DuckDuckGoTheme.colors.system.sliderTrackInactive,
        disabledThumbColor = DuckDuckGoTheme.colors.brand.accentBlue,
        disabledActiveTrackColor = DuckDuckGoTheme.colors.brand.accentBlue,
        disabledInactiveTrackColor = DuckDuckGoTheme.colors.system.sliderTrackInactive,
    )
}

@PreviewLightDark
@Composable
private fun DaxSliderAllStatesPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSliderPreviewState(value = 0f)
            DaxSliderPreviewState(value = 0.3f)
            DaxSliderPreviewState(value = 1f)
            DaxSliderPreviewState(value = 0.3f, enabled = false)
            DaxSliderPreviewState(value = 100f, valueRange = 70f..170f, steps = 9)
        }
    }
}

@Composable
private fun DaxSliderPreviewState(
    value: Float,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    DaxSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
    )
}
