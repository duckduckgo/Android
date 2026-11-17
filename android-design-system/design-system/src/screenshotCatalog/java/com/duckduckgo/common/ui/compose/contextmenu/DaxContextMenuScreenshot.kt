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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase
import com.duckduckgo.common.ui.compose.screenshot.ScreenshotFrame
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

object DaxContextMenuScreenshot : ScreenshotCase(name = "DaxContextMenu", frame = ScreenshotFrame.Screen(widthDp = 360, heightDp = 280)) {
    @Composable
    override fun Content() {
        PreviewBox(modifier = Modifier.fillMaxSize()) {
            DaxContextMenu(
                anchor = {
                    DaxIconButton(
                        onClick = {},
                        iconPainter = painterResource(R.drawable.ic_menu_vertical_24),
                        contentDescription = "More options",
                    )
                },
                expanded = true,
                onDismissRequest = {},
            ) {
                DaxIconItem(
                    text = "Bookmark",
                    painterLeadingIcon = painterResource(R.drawable.ic_bookmark_24),
                    onClick = {},
                )
                DaxInsetItem(
                    text = "Copy link",
                    onClick = {},
                    trailingIcon = { Icon(painterResource(R.drawable.ic_copy_24), null) },
                )
                DaxDefaultItem(text = "Delete", onClick = {}, isDestructive = true)
            }
        }
    }
}
