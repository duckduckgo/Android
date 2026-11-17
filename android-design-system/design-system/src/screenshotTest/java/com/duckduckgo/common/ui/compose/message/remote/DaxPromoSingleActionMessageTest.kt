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

package com.duckduckgo.common.ui.compose.message.remote

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.duckduckgo.common.ui.compose.DaxAction
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@SuppressLint("ComposePreviewPublic")
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DaxPromoSingleActionMessageTest() {
    PreviewBox {
        DaxPromoSingleActionMessage(
            title = "Promo single action message",
            body = "Body text goes here. This component has one button and an illustration",
            illustration = painterResource(R.drawable.laptop_ddginstall_96),
            illustrationContentDescription = null,
            action = DaxAction(text = "Promo Link", onClick = {}),
            onDismissed = {},
            modifier = Modifier.padding(dimensionResource(R.dimen.keyline_4)),
        )
    }
}
