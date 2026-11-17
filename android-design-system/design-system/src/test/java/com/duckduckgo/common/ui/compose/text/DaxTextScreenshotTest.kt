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

package com.duckduckgo.common.ui.compose.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import org.junit.Rule
import org.junit.Test

class DaxTextScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewBox {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DaxText(text = "Title", style = DuckDuckGoTheme.typography.title)
                    DaxText(text = "H1", style = DuckDuckGoTheme.typography.h1)
                    DaxText(text = "H2", style = DuckDuckGoTheme.typography.h2)
                    DaxText(text = "H3", style = DuckDuckGoTheme.typography.h3)
                    DaxText(text = "Body1", style = DuckDuckGoTheme.typography.body1)
                    DaxText(text = "Body2", style = DuckDuckGoTheme.typography.body2)
                    DaxText(text = "Caption", style = DuckDuckGoTheme.typography.caption)
                    DaxText(text = "Secondary color", color = DuckDuckGoTheme.textColors.secondary)
                    DaxText(text = "Long text truncated with an ellipsis because maxLines is one", maxLines = 1)
                }
            }
        }
    }
}
