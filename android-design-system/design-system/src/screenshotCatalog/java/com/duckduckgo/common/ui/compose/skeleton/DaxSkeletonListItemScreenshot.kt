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

package com.duckduckgo.common.ui.compose.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.tools.PreviewBox

object DaxSkeletonListItemScreenshot : ScreenshotCase(name = "DaxSkeletonListItem") {
    @Composable
    override fun Content() {
        PreviewBox {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DaxSkeletonListItem(animated = false)
                DaxSkeletonListItem(hasTwoLines = true, animated = false)
                DaxSkeletonListItem(hasLeadingIcon = false, animated = false)
            }
        }
    }
}
