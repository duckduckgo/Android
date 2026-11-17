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

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@SuppressLint("ComposePreviewPublic")
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DaxTopAppBarTest() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DaxTopAppBar(
                title = "Title",
                navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                actions = {
                    DaxIconButton(
                        onClick = {},
                        iconPainter = painterResource(R.drawable.ic_find_search_24),
                        contentDescription = "Search",
                    )
                },
            )
            DaxTopAppBar(
                title = "A very long title that should be truncated with an ellipsis at the end",
                navigationIcon = DaxTopAppBarNavigationIcon.Close { },
                shadow = true,
            )
            DaxTopAppBar(title = "No navigation icon")
        }
    }
}
