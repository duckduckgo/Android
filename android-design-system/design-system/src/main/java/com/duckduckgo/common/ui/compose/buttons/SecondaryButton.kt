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

package com.duckduckgo.common.ui.compose.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

/**
 * TODO When buttons will be available in the design system, replace this with the official SecondaryButton composable.
 */
@Composable
fun LargeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    )
}

/**
 * TODO When buttons will be available in the design system, replace this with the official SecondaryButton composable.
 */
@Composable
fun SmallSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
    )
}

/**
 * TODO When buttons will be available in the design system, replace this with the official SecondaryButton composable.
 */
@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DuckDuckGoTheme.colors.brand.accentBlue,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = DuckDuckGoTheme.colors.brand.accentBlue,
        ),
        content = {
            DaxText(
                text = text,
                color = DuckDuckGoTheme.colors.brand.accentBlue,
                style = DuckDuckGoTheme.typography.button,
            )
        },
    )
}
