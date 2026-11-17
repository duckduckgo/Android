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

package com.duckduckgo.common.ui.compose.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

object DaxBrandButtonScreenshot : ScreenshotCase(name = "DaxBrandButton") {
    @Composable
    override fun Content() {
        PreviewBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DaxBrandButton(text = "Small", onClick = {})
                DaxBrandButton(text = "Large", onClick = {}, size = DaxButtonSize.Large)
                DaxBrandButton(text = "With icon", onClick = {}, leadingIconPainter = painterResource(R.drawable.ic_add_24_solid_color))
                DaxBrandButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
    }
}
