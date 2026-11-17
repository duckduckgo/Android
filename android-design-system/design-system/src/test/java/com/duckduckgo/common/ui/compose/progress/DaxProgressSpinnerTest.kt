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

package com.duckduckgo.common.ui.compose.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotTest
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import org.junit.Test

class DaxProgressSpinnerTest : ScreenshotTest() {

    @Test
    fun screenshot() = captureComposable {
        PreviewBox {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DaxProgressSpinner()
                DaxProgressSpinner(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
