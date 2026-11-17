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

package com.duckduckgo.common.ui.compose.snackbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.duckduckgo.common.ui.compose.DaxAction
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import org.junit.Rule
import org.junit.Test

class DaxSnackbarScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(renderingMode = RenderingMode.SHRINK)

    @Test
    fun screenshot() {
        paparazzi.snapshot {
            PreviewBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DaxSnackbar(message = "Fireproofing removed.")
                    DaxSnackbar(
                        message = "Text of the Snackbar can span to several lines.",
                        action = DaxAction(text = "Undo", onClick = {}),
                    )
                    DaxSnackbar(
                        message = "This snackbar message is far too long to fit, so it is capped at two lines and " +
                            "truncated with an ellipsis rather than growing unbounded beside the action.",
                        action = DaxAction(text = "Action", onClick = {}),
                    )
                }
            }
        }
    }
}
