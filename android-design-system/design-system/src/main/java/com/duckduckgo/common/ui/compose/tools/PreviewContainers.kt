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

package com.duckduckgo.common.ui.compose.tools

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@Composable
fun PreviewBoxInverted(
    modifier: Modifier = Modifier,
    color: @Composable () -> Color = { DuckDuckGoTheme.colors.backgrounds.backgroundInverted },
    content: @Composable BoxScope.() -> Unit,
) = PreviewBox(
    modifier = modifier,
    color = color,
    content = content,
)

@Composable
fun PreviewBox(
    modifier: Modifier = Modifier,
    color: @Composable () -> Color = { DuckDuckGoTheme.colors.backgrounds.background },
    content: @Composable BoxScope.() -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val baseContext = LocalContext.current
    // Previews lack the View theme that painterResource uses to resolve drawable `?attr/daxColor*` fills, so without it icons render transparent.
    val themedContext = remember(baseContext, isDarkTheme) {
        ContextThemeWrapper(
            baseContext,
            if (isDarkTheme) R.style.Theme_DuckDuckGo_Dark else R.style.Theme_DuckDuckGo_Light,
        )
    }
    CompositionLocalProvider(LocalContext provides themedContext) {
        DuckDuckGoTheme(isDarkTheme = isDarkTheme) {
            Box(
                modifier = modifier
                    .background(color())
                    .padding(all = 16.dp),
                content = content,
            )
        }
    }
}

/**
 * Themed, unpadded preview container backed by [DaxSurface], for edge-to-edge components such as
 * list items that fill the width and carry their own internal padding. Unlike [PreviewBox] it adds
 * no padding and renders on a real surface (flat, no elevation).
 */
@Composable
fun PreviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DuckDuckGoTheme {
        DaxSurface(
            modifier = modifier,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}
