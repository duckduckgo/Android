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

package com.duckduckgo.common.ui.compose.checkbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.tools.PreviewBox

object DaxCheckboxScreenshot : ScreenshotCase(name = "DaxCheckbox") {
    @Composable
    override fun Content() {
        PreviewBox {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaxCheckbox(checked = false, onCheckedChange = {})
                DaxCheckbox(checked = true, onCheckedChange = {})
                DaxCheckbox(checked = false, onCheckedChange = {}, enabled = false)
                DaxCheckbox(checked = true, onCheckedChange = {}, enabled = false)
            }
        }
    }
}
