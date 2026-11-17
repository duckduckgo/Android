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

package com.duckduckgo.common.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotTest
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import org.junit.Test

class DaxStatusIndicatorTest : ScreenshotTest() {

    @Test
    fun screenshot() = captureComposable {
        PreviewBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DaxStatusIndicator(status = Status.AlwaysOn)
                DaxStatusIndicator(status = Status.On)
                DaxStatusIndicator(status = Status.Off)
            }
        }
    }
}
