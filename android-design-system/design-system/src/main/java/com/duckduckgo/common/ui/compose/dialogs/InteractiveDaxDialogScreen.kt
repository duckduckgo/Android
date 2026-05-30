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

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.buttons.LargePrimaryButton
import com.duckduckgo.common.ui.compose.buttons.PrimaryButton
import com.duckduckgo.common.ui.compose.dialogs.wave.WaveEdge
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import kotlin.random.Random

data class InteractiveDaxDialogUi(
    val progressIndex: Int = 0,
    val progressCurrent: Int = 1,
    val edgePosition: WaveEdge = WaveEdge.Bottom,
    val wavePosition: Float = 0.1f,
    val dismissable: Boolean = false
) {
    fun isWaveSelected(index: Int) = WaveEdge.values()[index] == edgePosition
    fun isProgressSelected(index: Int) = index == progressIndex
}

@Composable
fun InteractiveDaxDialogScreen(
    modifier: Modifier = Modifier,
) {
    var modelUi by remember { mutableStateOf(InteractiveDaxDialogUi()) }
    val progressOptions = remember { listOf("None", "3", "5") }
    val edgeOptions = remember { listOf("Bottom", "Top", "Left", "Right") }
    val animatedPosition by animateFloatAsState(
        targetValue = modelUi.wavePosition,
        animationSpec = tween(durationMillis = 800),
        label = "position_animation",
    )
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .background(DuckDuckGoTheme.colors.backgrounds.background)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            DaxDialog(
                title = "Hi there!",
                description = "Ready for a faster browser that keeps you protected?",
                progress = if (modelUi.progressIndex == 0) {
                    null
                } else {
                    @Composable {
                        if (modelUi.progressIndex == 1) {
                            DaxProgressBarOf3(current = modelUi.progressCurrent)
                        } else {
                            DaxProgressBarOf5(current = modelUi.progressCurrent)
                        }
                    }
                },
                waveEdge = modelUi.edgePosition,
                wavePosition = animatedPosition,
                dismiss = if (modelUi.dismissable) {
                    {
                        Toast.makeText(context, "Dialog dismissed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    null
                },
                buttons = {
                    LargePrimaryButton(
                        text = "Let's do it!",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 30.dp, horizontal = 16.dp),
        ) {
            stickyHeader {
                DaxText(
                    text = "Change wave edge",
                    style = DuckDuckGoTheme.typography.h2,
                )
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SingleChoiceSegmentedButtonRow {
                        edgeOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                label = {
                                    DaxText(
                                        text = option,
                                        style = DuckDuckGoTheme.typography.button,
                                        color = if (modelUi.isWaveSelected(index)) {
                                            DuckDuckGoTheme.colors.text.primaryInverted
                                        } else {
                                            DuckDuckGoTheme.colors.text.primary
                                        },
                                    )
                                },
                                selected = modelUi.isWaveSelected(index),
                                onClick = {
                                    when (index) {
                                        0 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Bottom)
                                        1 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Top)
                                        2 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Left)
                                        3 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Right)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = edgeOptions.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContentColor = DuckDuckGoTheme.colors.text.primaryInverted,
                                    activeBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    activeContainerColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    inactiveContentColor = DuckDuckGoTheme.colors.text.primary,
                                    inactiveBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    inactiveContainerColor = DuckDuckGoTheme.colors.backgrounds.background,
                                ),
                            )
                        }
                    }
                }
                PrimaryButton(
                    text = "Move on edge",
                    onClick = {
                        modelUi = modelUi.copy(wavePosition = (0.1f..0.9f).random())
                    },
                )
            }
            stickyHeader {
                DaxText(
                    text = "Progress bar",
                    style = DuckDuckGoTheme.typography.h2,
                )
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SingleChoiceSegmentedButtonRow {
                        progressOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                label = {
                                    DaxText(
                                        text = option,
                                        style = DuckDuckGoTheme.typography.button,
                                        color = if (modelUi.isProgressSelected(index)) {
                                            DuckDuckGoTheme.colors.text.primaryInverted
                                        } else {
                                            DuckDuckGoTheme.colors.text.primary
                                        },
                                    )
                                },
                                selected = modelUi.isProgressSelected(index),
                                onClick = { modelUi = modelUi.copy(progressIndex = index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = progressOptions.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContentColor = DuckDuckGoTheme.colors.text.primaryInverted,
                                    activeBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    activeContainerColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    inactiveContentColor = DuckDuckGoTheme.colors.text.primary,
                                    inactiveBorderColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                    inactiveContainerColor = DuckDuckGoTheme.colors.backgrounds.background,
                                ),
                            )
                        }
                    }
                }
                Slider(
                    value = modelUi.progressCurrent.toFloat(),
                    onValueChange = { modelUi = modelUi.copy(progressCurrent = it.toInt()) },
                    valueRange = (1f..if (modelUi.progressIndex == 1) 3f else 5f),
                    enabled = modelUi.progressIndex != 0,
                    colors = SliderDefaults.colors(
                        thumbColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        activeTrackColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        activeTickColor = DuckDuckGoTheme.colors.brand.accentBlue,
                        inactiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand50,
                        inactiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand50,
                        disabledThumbColor = DuckDuckGoTheme.colors.brand.accentBrand50,
                        disabledInactiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand20,
                        disabledInactiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand20,
                        disabledActiveTrackColor = DuckDuckGoTheme.colors.brand.accentBrand50,
                        disabledActiveTickColor = DuckDuckGoTheme.colors.brand.accentBrand50
                    ),
                )
            }
            stickyHeader {
                DaxText(
                    text = "Dismissable",
                    style = DuckDuckGoTheme.typography.h2,
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = modelUi.dismissable,
                        onCheckedChange = { modelUi = modelUi.copy(dismissable = it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = DuckDuckGoTheme.colors.brand.accentBlue,
                            uncheckedColor = DuckDuckGoTheme.colors.text.primary,
                            checkmarkColor = DuckDuckGoTheme.colors.text.primaryInverted,
                        )
                    )
                    DaxText("Allow dialog to be dismissed")
                }
            }
        }
    }
}

fun ClosedFloatingPointRange<Float>.random() =
    Random.nextDouble(start.toDouble(), endInclusive.toDouble()).toFloat()

@PreviewLightDark
@Preview
@Composable
private fun InteractiveDaxDialogScreenPreview() {
    DuckDuckGoTheme {
        InteractiveDaxDialogScreen(
            modifier = Modifier
                .fillMaxSize()
                .background(DuckDuckGoTheme.colors.backgrounds.window),
        )
    }
}
