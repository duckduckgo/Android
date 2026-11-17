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

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.cash.paparazzi.Paparazzi
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarNavigationIcon
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R
import org.junit.Rule
import org.junit.Test

class DaxScaffoldScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            DuckDuckGoTheme {
                DaxScaffold(
                    topBar = {
                        DaxTopAppBar(
                            title = "Bookmarks",
                            navigationIcon = DaxTopAppBarNavigationIcon.Back { },
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
    }
}
