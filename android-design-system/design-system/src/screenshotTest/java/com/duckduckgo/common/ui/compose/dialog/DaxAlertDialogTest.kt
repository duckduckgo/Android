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

package com.duckduckgo.common.ui.compose.dialog

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@SuppressLint("ComposePreviewPublic")
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DaxAlertDialogTest() {
    PreviewBox {
        DaxAlertDialogContent(
            title = "Dialog Title",
            message = {
                DaxText(
                    text = "This dialog uses every slot the base composable exposes.",
                    style = DuckDuckGoTheme.typography.body1,
                    color = DuckDuckGoTheme.textColors.secondary,
                )
            },
            headerImage = painterResource(R.drawable.ic_dax_icon),
            buttons = {
                DaxGhostButton(text = "Cancel", onClick = {})
                DaxPrimaryButton(text = "Confirm", onClick = {})
            },
        )
    }
}
