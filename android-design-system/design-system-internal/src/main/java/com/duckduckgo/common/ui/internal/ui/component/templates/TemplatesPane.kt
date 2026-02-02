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

package com.duckduckgo.common.ui.internal.ui.component.templates

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.template.DaxPageHeader
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R as CommonR

@Composable
fun TemplatesPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyline4 = dimensionResource(CommonR.dimen.keyline_4)
    val state = rememberLazyListState()
    LazyColumn(
        modifier = modifier.background(color = DuckDuckGoTheme.colors.backgrounds.background),
        state = state,
        contentPadding = PaddingValues(all = keyline4),
        verticalArrangement = Arrangement.spacedBy(space = keyline4),
    ) {
        item {
            DaxText(
                text = "Page Header (Settings)",
                style = DuckDuckGoTheme.typography.h4,
                color = DuckDuckGoTheme.colors.text.tertiary,
                modifier = Modifier.padding(vertical = keyline4),
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                status = Status.ALWAYS_ON,
                iconHeader = painterResource(CommonR.drawable.ic_privacy_pro_128),
                body = "DuckDuckGo Private Search is your default search engine, so you can search the web without being tracked.",
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                subtitle = "Lorem impsum dolor sit amet",
                status = Status.ALWAYS_ON,
                iconHeader = painterResource(CommonR.drawable.ic_privacy_pro_128),
                body = "DuckDuckGo Private Search is your default search engine, so you can search the web without being tracked.",
                learnMoreClick = {
                    Toast.makeText(context, "Learn more clicked", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
