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
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.tools.PreviewSurface
import com.duckduckgo.mobile.android.R
import org.junit.Rule
import org.junit.Test

class DaxSettingsListItemScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewSurface {
                Column {
                    DaxSettingsListItem(primaryText = "Status on", trailingContent = { StatusIndicator(Status.On) }, onClick = {})
                    DaxSettingsListItem(
                        primaryText = "With pill",
                        inlineContent = { Pill("Beta") },
                        trailingContent = { StatusIndicator(Status.AlwaysOn) },
                        onClick = {},
                    )
                    DaxSettingsListItem(
                        primaryText = "With leading icon",
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_globe_24), null, background = DaxListItemIconBackground.Circular)
                        },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_open_in_16), "Open", size = DaxListItemTrailingIconSize.Small)
                        },
                        onClick = {},
                    )
                    DaxSettingsListItem(
                        primaryText = "Subscription",
                        secondaryText = "Your subscription expired",
                        leadingContent = { Icon(painterResource(R.drawable.ic_globe_24), null) },
                        trailingContent = {
                            Icon(painterResource(R.drawable.ic_exclamation_recolorable_16), "Expired", size = DaxListItemTrailingIconSize.Small)
                        },
                        onClick = {},
                    )
                    DaxSettingsListItem(
                        primaryText = "Disabled",
                        trailingContent = { StatusIndicator(Status.Off) },
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}
