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

package com.duckduckgo.common.ui.compose.listitem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.painterResource
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotTest
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R
import org.junit.Test

class DaxTwoLineListItemTest : ScreenshotTest() {

    @Test
    fun screenshot() = captureComposable {
        PreviewSurface {
            Column {
                DaxTwoLineListItem(primaryText = "Primary label", secondaryText = "Secondary caption", onClick = {})
                DaxTwoLineListItem(
                    primaryText = "With leading icon",
                    secondaryText = "Supporting text",
                    leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null, background = DaxListItemIconBackground.Circular) },
                    onClick = {},
                )
                DaxTwoLineListItem(
                    primaryText = "With switch",
                    secondaryText = "Supporting text",
                    trailingContent = { Switch(checked = true, onCheckedChange = {}) },
                )
                DaxTwoLineListItem(primaryText = "Disabled", secondaryText = "Supporting text", enabled = false, onClick = {})
            }
        }
    }
}
