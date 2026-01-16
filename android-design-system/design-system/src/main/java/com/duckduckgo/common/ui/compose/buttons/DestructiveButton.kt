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

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

/**
 * Large destructive button with 48dp minimum height.
 */
@Composable
fun LargeDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DestructiveButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
    )
}

/**
 * Small destructive button with 36dp minimum height.
 */
@Composable
fun SmallDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DestructiveButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        enabled = enabled,
    )
}

/**
 * Destructive button composable for the DuckDuckGo design system.
 * Used for destructive actions like delete or remove.
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DuckDuckGoTheme.textColors.destructive,
            contentColor = DuckDuckGoTheme.colors.icons.white,
            disabledContainerColor = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
            disabledContentColor = DuckDuckGoTheme.textColors.disabled,
        ),
        content = {
            DaxText(
                text = text,
                color = if (enabled) DuckDuckGoTheme.colors.icons.white else DuckDuckGoTheme.textColors.disabled,
                style = DuckDuckGoTheme.typography.button,
            )
        },
    )
}

/**
 * Large ghost destructive button with 48dp minimum height.
 */
@Composable
fun LargeGhostDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GhostDestructiveButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
    )
}

/**
 * Small ghost destructive button with 36dp minimum height.
 */
@Composable
fun SmallGhostDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GhostDestructiveButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        enabled = enabled,
    )
}

/**
 * Ghost destructive button composable for the DuckDuckGo design system.
 * Used for secondary destructive actions.
 */
@Composable
fun GhostDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = DuckDuckGoTheme.textColors.destructive,
            disabledContentColor = DuckDuckGoTheme.textColors.disabled,
        ),
        content = {
            DaxText(
                text = text,
                color = if (enabled) DuckDuckGoTheme.textColors.destructive else DuckDuckGoTheme.textColors.disabled,
                style = DuckDuckGoTheme.typography.button,
            )
        },
    )
}
