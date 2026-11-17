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

package com.duckduckgo.common.ui.compose.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotFrame
import com.duckduckgo.common.ui.compose.screenshot.rememberExpandedSheetState
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

object DaxActionBottomSheetDialogScreenshot : ScreenshotCase(
    name = "DaxActionBottomSheetDialog",
    frame = ScreenshotFrame.Screen(widthDp = 360, heightDp = 640),
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        DuckDuckGoTheme {
            DaxActionBottomSheetDialog(
                title = "Actions",
                onDismissRequest = {},
                sheetState = rememberExpandedSheetState(),
                content = {
                    item {
                        DaxOneLineListItem(
                            primaryText = "Add",
                            leadingContent = { Icon(painterResource(R.drawable.ic_add_24_solid_color), null) },
                            onClick = {},
                        )
                    }
                    item {
                        DaxOneLineListItem(
                            primaryText = "Ask Duck.ai",
                            leadingContent = { Icon(painterResource(R.drawable.ic_ai_chat_24_solid_color), null) },
                            onClick = {},
                        )
                    }
                },
            )
        }
    }
}
