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

package com.duckduckgo.common.ui.internal.ui.compose.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun InteractiveComponentTemplate(
    modifier: Modifier = Modifier,
    minHeight: Dp = 300.dp,
    componentBackgroundColor: Color = DuckDuckGoTheme.colors.backgrounds.background,
    interactiveBackgroundColor: Color = DuckDuckGoTheme.colors.backgrounds.surface,
    componentContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    interactiveContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 30.dp),
    component: @Composable BoxScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().background(color = interactiveBackgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(color = componentBackgroundColor)
                .padding(paddingValues = componentContentPadding),
            contentAlignment = Alignment.Center,
            content = component,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = interactiveContentPadding,
            content = content,
        )
    }
}
