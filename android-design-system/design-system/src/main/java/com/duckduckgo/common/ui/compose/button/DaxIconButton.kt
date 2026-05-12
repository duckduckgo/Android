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

package com.duckduckgo.common.ui.compose.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed icon button.
 *
 * Wraps Material3 [IconButton] for icon-only actions (e.g. close, back, overflow).
 *
 * No tint is applied — the icon renders with its native colors, matching the
 * View-system [IconButton][com.duckduckgo.common.ui.view.button.IconButton] which
 * relies on the drawable's own `?attr/` theme colors (e.g. `?attr/daxColorPrimaryIcon`).
 *
 * @param onClick Called when the button is clicked.
 * @param iconPainter The icon to display.
 * @param contentDescription Accessibility description, or null if decorative.
 * @param modifier Modifier for this button.
 * @param interactionSource The interaction source for this button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxIconButton(
    onClick: () -> Unit,
    iconPainter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            interactionSource = interactionSource,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = Color.Unspecified,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxIconButtonPreview() {
    PreviewBox {
        DaxIconButton(
            onClick = {},
            iconPainter = painterResource(R.drawable.ic_settings_24),
            contentDescription = "Settings",
        )
    }
}
