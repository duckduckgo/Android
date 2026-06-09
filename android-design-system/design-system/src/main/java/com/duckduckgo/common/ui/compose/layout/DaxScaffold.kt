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

package com.duckduckgo.common.ui.compose.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed Scaffold layout.
 * Wraps Material3 [Scaffold] with DuckDuckGo's default colors and styling.
 *
 * @param modifier Modifier for this Scaffold.
 * @param topBar Composable for the top app bar.
 * @param bottomBar Composable for the bottom bar.
 * @param snackbarHost Composable for the snackbar host.
 * @param contentWindowInsets Window insets for the content area.
 * @param content Composable for the main content, with padding values provided by Scaffold.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215519019861192?focus=true
 */
@Composable
fun DaxScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = DaxScaffoldDefaults.containerColor,
        contentColor = DaxScaffoldDefaults.contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

object DaxScaffoldDefaults {
    val containerColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.background

    val contentColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.primary
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxScaffoldPreview() {
    DuckDuckGoTheme {
        DaxScaffold(
            topBar = {
                DaxTopAppBar(
                    title = "Bookmarks",
                    navigationIcon = {
                        Back { }
                    },
                    actions = {
                        DaxIconButton(
                            iconPainter = painterResource(R.drawable.ic_ai_chat_24_solid_color),
                            contentDescription = "Duck.ai",
                            onClick = { },
                        )
                    },
                )
            },
        ) { paddingValues ->
            DaxText(
                text = "Content goes here",
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
