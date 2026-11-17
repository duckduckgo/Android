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

package com.duckduckgo.common.ui.compose.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R
import org.junit.Rule
import org.junit.Test

class DaxSearchTopAppBarScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewBox {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DaxSearchTopAppBar(
                        title = "Title",
                        searchActive = false,
                        searchState = rememberTextFieldState(),
                        searchPlaceholder = "Search…",
                        navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                        actions = {
                            DaxIconButton(
                                onClick = {},
                                iconPainter = painterResource(R.drawable.ic_find_search_24),
                                contentDescription = "Search",
                            )
                        },
                    )
                    DaxSearchTopAppBar(
                        title = "Title",
                        searchActive = true,
                        searchState = rememberTextFieldState(),
                        searchPlaceholder = "Search…",
                        navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                    )
                    DaxSearchTopAppBar(
                        title = "Title",
                        searchActive = true,
                        searchState = rememberTextFieldState("query"),
                        searchPlaceholder = "Search…",
                        navigationIcon = DaxTopAppBarNavigationIcon.Close { },
                    )
                }
            }
        }
    }
}
