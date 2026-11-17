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

import android.annotation.SuppressLint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@SuppressLint("ComposePreviewPublic")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun DaxPromoBottomSheetDialogTest() {
    DuckDuckGoTheme {
        DaxPromoBottomSheetDialog(
            title = "Title",
            description = "Add our search widget to your home screen for quick access.",
            promoIcon = painterResource(R.drawable.announcement_96),
            buttons = {
                DaxGhostButton(text = "Not now", onClick = {})
                DaxPrimaryButton(text = "Add widget", onClick = {})
            },
            onDismissRequest = {},
        )
    }
}
