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

package com.duckduckgo.common.ui.compose.contextmenu

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.painterResource
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R
import org.junit.Rule
import org.junit.Test

class DaxInsetContextMenuItemScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewSurface {
                Column {
                    DaxInsetContextMenuItem(text = "With divider", onClick = {}, showDivider = true)
                    DaxInsetContextMenuItem(
                        text = "Trailing icon",
                        onClick = {},
                        trailingIcon = { Icon(painterResource(R.drawable.ic_copy_24), null) },
                    )
                    DaxInsetContextMenuItem(text = "Disabled", onClick = {}, enabled = false)
                    DaxInsetContextMenuItem(text = "Destructive", onClick = {}, isDestructive = true)
                }
            }
        }
    }
}
