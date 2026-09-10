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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.dialogs.dismiss.DaxDismissIconButton
import com.duckduckgo.common.ui.compose.dialogs.progress.DaxDialogProgressScope
import com.duckduckgo.common.ui.compose.dialogs.wave.WaveEdge
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

@Composable
fun DaxDialog(
    title: String?,
    description: String?,
    modifier: Modifier = Modifier,
    waveEdge: WaveEdge = WaveEdge.Bottom,
    wavePosition: Float = 0.3f,
    dismiss: (() -> Unit)? = null,
    progress: (@Composable DaxDialogProgressScope.() -> Unit)? = null,
    buttons: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val topProgressPadding = when {
        progress != null -> 15.dp
        dismiss != null -> 8.dp
        else -> 0.dp
    }
    val endDismissPadding = if (dismiss != null) {
        4.dp
    } else {
        0.dp
    }
    Box(modifier = modifier) {
        DaxDialogSurface(
            edge = waveEdge,
            wavePosition = wavePosition,
            modifier = Modifier.padding(top = topProgressPadding, end = endDismissPadding)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (title != null || description != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(DaxDialogDefaults.headerContentPadding)
                    ) {
                        if (title != null) {
                            DaxText(
                                text = title,
                                style = DaxDialogDefaults.titleStyle,
                            )
                        }
                        if (description != null) {
                            DaxText(
                                text = description,
                                style = DaxDialogDefaults.descriptionStyle,
                            )
                        }
                    }
                }
                if (content != null) {
                    content()
                }
                if (buttons != null) {
                    buttons()
                }
            }
        }
        if (progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 40.dp)
            ) {
                DaxDialogProgressScope.progress()
            }
        }
        if (dismiss != null) {
            val topDismissPadding = if (progress != null) {
                8.dp
            } else {
                0.dp
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topDismissPadding)
            ) {
                DaxDismissIconButton(onClick = dismiss)
            }
        }
    }
}

private object DaxDialogDefaults {
    val titleStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.h1

    val descriptionStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body1

    val headerContentPadding: PaddingValues = PaddingValues(top = 0.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
}

@PreviewLightDark
@Composable
private fun DaxDialogPreview() {
    PreviewBox {
        DaxDialog(
            title = "Hi there!",
            description = "Ready for a faster browser that keeps you protected?",
            dismiss = {},
            progress = {
                DaxProgressBarOf5(current = 2)
            },
            buttons = {
                DaxPrimaryButton(
                    text = "Let's do it!",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    size = DaxButtonSize.Large,
                )
            }
        )
    }
}
