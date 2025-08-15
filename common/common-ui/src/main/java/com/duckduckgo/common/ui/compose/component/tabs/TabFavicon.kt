/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.component.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@Composable
fun TabFavicon(
    iconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = iconModel,
            placeholder = painterResource(R.drawable.ic_globe_16),
            error = painterResource(R.drawable.ic_globe_16),
            contentDescription = "Site Icon",
            modifier = Modifier.size(16.dp),
        )

        if (isUnreadIndicatorVisible) {
            TabUnreadIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
            )
        }
    }
}

@Composable
private fun TabUnreadIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    color = DuckDuckGoTheme.colors.primaryInvertedText,
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = DuckDuckGoTheme.colors.accentBlue,
                    shape = CircleShape,
                ),
        )
    }
}

@PreviewLightDark
@Composable
private fun TabUnreadIndicatorPreview() {
    DuckDuckGoTheme {
        TabFavicon(
            iconModel = null,
            isUnreadIndicatorVisible = true,
        )
    }
}
