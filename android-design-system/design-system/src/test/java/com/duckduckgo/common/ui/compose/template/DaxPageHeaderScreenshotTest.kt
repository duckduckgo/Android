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

package com.duckduckgo.common.ui.compose.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R
import org.junit.Rule
import org.junit.Test

class DaxPageHeaderScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewBox {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DaxPageHeader(
                        title = "Privacy Protection",
                        subtitle = "Your privacy is our priority. We help you stay safe online with built-in tracking protection and private search.",
                        body = "Privacy Pro is actively protecting you from trackers on the web and in apps.",
                        iconHeader = painterResource(R.drawable.subscription_128),
                        status = Status.Off,
                        learnMoreClick = {},
                    )
                    DaxHorizontalDivider()
                    DaxPageHeader(title = "Title only")
                }
            }
        }
    }
}
