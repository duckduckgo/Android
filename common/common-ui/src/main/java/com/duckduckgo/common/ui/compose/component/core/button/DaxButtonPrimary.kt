/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.component.core.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.component.core.text.DaxTextPrimary
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DaxButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButton(
        onClick = onClick,
        colors = primaryColors(),
        modifier = modifier,
        enabled = enabled
    ) {
        DaxButtonText(text)
    }
}

@Composable
fun DaxButtonPrimaryLarge(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DaxButtonLarge(
        onClick = onClick,
        colors = primaryColors(),
        modifier = modifier,
        enabled = enabled
    ) {
        DaxButtonText(text)
    }
}

@Composable
private fun primaryColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = DuckDuckGoTheme.colors.accentBlue,
    contentColor = DuckDuckGoTheme.colors.text.primaryInverted,
    disabledContainerColor = DuckDuckGoTheme.colors.containerDisabled,
    disabledContentColor = DuckDuckGoTheme.colors.textDisabled
)

@PreviewLightDark
@Composable
private fun DaxButtonPrimaryPreview() {
    DuckDuckGoTheme {
        PreviewBox {
            DaxButtonPrimary(
                text = "Primary",
                onClick = { }
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonPrimaryLargePreview() {
    DuckDuckGoTheme {
        PreviewBox {
            DaxButtonPrimaryLarge(
                text = "Primary Large",
                onClick = { }
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxButtonPrimaryDisabledPreview() {
    DuckDuckGoTheme {
        PreviewBox {
            DaxButtonPrimary(
                text = "Primary Disabled",
                onClick = { },
                enabled = false
            )
        }
    }
}
