/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.mobile.android.R
import com.duckduckgo.app.browser.R as BrowserR

@Composable
fun Omnibar(modifier: Modifier = Modifier) {
    TopAppBar(
        backgroundColor = Color.White,
        modifier = modifier,
    ) {
        BasicTextField(
            value = "",
            singleLine = true,
            onValueChange = {},
/*            leadingIcon = {
                Icon(
                    painter = painterResource(BrowserR.drawable.ic_find_search_20_a05),
                    contentDescription = null,
                )
            },*/
            colors =
                TextFieldDefaults.textFieldColors(
                    backgroundColor = colorResource(R.color.black6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(20.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun OmnibarPreview() {
    Omnibar(modifier = Modifier.fillMaxWidth())
}
