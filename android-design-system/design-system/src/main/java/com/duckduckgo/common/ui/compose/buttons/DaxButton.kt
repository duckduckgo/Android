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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Button types for the DuckDuckGo design system.
 */
enum class DaxButtonType {
    PRIMARY,
    GHOST,
    SECONDARY,
    DESTRUCTIVE,
    GHOST_DESTRUCTIVE,
    GHOST_ALT,
}

/**
 * Renders a button based on the specified [DaxButtonType].
 *
 * @param text The button label text
 * @param buttonType The style of button to render
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier to be applied to the button
 * @param enabled Whether the button is enabled
 */
@Composable
fun DaxButton(
    text: String,
    buttonType: DaxButtonType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (buttonType) {
        DaxButtonType.PRIMARY -> PrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        DaxButtonType.SECONDARY -> SecondaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        DaxButtonType.GHOST, DaxButtonType.GHOST_ALT -> GhostButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        DaxButtonType.DESTRUCTIVE -> DestructiveButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        DaxButtonType.GHOST_DESTRUCTIVE -> GhostDestructiveButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    }
}
