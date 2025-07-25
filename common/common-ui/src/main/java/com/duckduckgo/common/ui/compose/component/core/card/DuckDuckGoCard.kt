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

package com.duckduckgo.common.ui.compose.component.core.card

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

@Composable
fun DuckDuckGoCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DuckDuckGoTheme.colors.surface,
            contentColor = DuckDuckGoTheme.colors.primaryText,
            disabledContainerColor = DuckDuckGoTheme.colors.containerDisabled,
            disabledContentColor = DuckDuckGoTheme.colors.textDisabled,
        ),
        shape = DuckDuckGoTheme.shapes.medium,
        onClick = onClick,
        modifier = modifier,
    ) {
        content()
    }
}

@PreviewLightDark
@Composable
private fun DuckDuckGoCardPreview() {
    DuckDuckGoCard(
        content = { Text(text = "This is a DuckDuckGoCard") },
    )
}

