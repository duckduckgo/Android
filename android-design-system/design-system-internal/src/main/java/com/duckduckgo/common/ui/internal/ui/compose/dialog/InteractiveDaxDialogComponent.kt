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

package com.duckduckgo.common.ui.internal.ui.compose.dialog

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox
import com.duckduckgo.common.ui.compose.dialogs.DaxDialog
import com.duckduckgo.common.ui.compose.dialogs.wave.WaveEdge
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.internal.ui.compose.interactives.DaxSingleChoiceSegmentedButtonRow
import com.duckduckgo.common.ui.internal.ui.compose.interactives.DaxSlider
import com.duckduckgo.common.ui.internal.ui.compose.interactives.SelectableOneLineListItem
import com.duckduckgo.common.ui.internal.ui.compose.templates.InteractiveComponentTemplate
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
fun InteractiveDaxDialogComponent(
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
    InteractiveComponentTemplate(
        modifier = modifier,
        component = {
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
                    DaxPrimaryButton(
                        text = "Let's do it!",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        size = DaxButtonSize.Large
                    )
                },
            )
        },
        content = {
            stickyHeader {
                DaxText(
                    text = "Change wave edge",
                    style = DuckDuckGoTheme.typography.h2,
                )
            }
            item {
                SelectableOneLineListItem(
                    title = "Wave edge",
                    selected = edgeOptions.firstOrNull { modelUi.isWaveSelected(edgeOptions.indexOf(it)) }
                        ?: edgeOptions.first(),
                    options = edgeOptions,
                    onClick = {
                        val index = edgeOptions.indexOf(it)
                        when (index) {
                            0 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Bottom)
                            1 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Top)
                            2 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Left)
                            3 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Right)
                        }
                    },
                )
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    DaxSingleChoiceSegmentedButtonRow(
                        options = edgeOptions,
                        selectedOption = edgeOptions.firstOrNull { modelUi.isWaveSelected(edgeOptions.indexOf(it)) }
                            ?: edgeOptions.first(),
                        onOptionSelected = { selected ->
                            val index = edgeOptions.indexOf(selected)
                            when (index) {
                                0 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Bottom)
                                1 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Top)
                                2 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Left)
                                3 -> modelUi = modelUi.copy(edgePosition = WaveEdge.Right)
                            }
                        },
                    )
                }
                DaxPrimaryButton(
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
                    DaxSingleChoiceSegmentedButtonRow(
                        options = progressOptions,
                        selectedOption = progressOptions.firstOrNull { modelUi.isProgressSelected(progressOptions.indexOf(it)) }
                            ?: progressOptions.first(),
                        onOptionSelected = { selected ->
                            val index = progressOptions.indexOf(selected)
                            modelUi = modelUi.copy(progressIndex = index, progressCurrent = 1)
                        },
                    )
                }
                DaxSlider(
                    value = modelUi.progressCurrent.toFloat(),
                    onValueChange = { modelUi = modelUi.copy(progressCurrent = it.toInt()) },
                    valueRange = (1f..if (modelUi.progressIndex == 1) 3f else 5f),
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
                    DaxCheckbox(
                        checked = modelUi.dismissable,
                        onCheckedChange = { modelUi = modelUi.copy(dismissable = it) },
                    )
                    DaxText("Allow dialog to be dismissed")
                }
            }
        }
    )
}

fun ClosedFloatingPointRange<Float>.random() =
    Random.nextDouble(start.toDouble(), endInclusive.toDouble()).toFloat()

@PreviewLightDark
@Composable
private fun InteractiveDaxDialogComponentPreview() {
    DuckDuckGoTheme {
        InteractiveDaxDialogComponent(
            modifier = Modifier
                .fillMaxSize()
                .background(DuckDuckGoTheme.colors.backgrounds.window),
        )
    }
}
