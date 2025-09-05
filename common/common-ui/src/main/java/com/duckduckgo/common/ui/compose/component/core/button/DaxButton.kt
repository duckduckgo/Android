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

package com.duckduckgo.common.ui.compose.component.core.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.component.core.text.DaxTextPrimary
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@Composable
internal fun DaxButton(
    onClick: () -> Unit,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = dimensionResource(R.dimen.buttonSmallHeight),
    contentPadding: PaddingValues = PaddingValues(
        horizontal = dimensionResource(R.dimen.buttonSmallSidePadding),
        vertical = dimensionResource(R.dimen.buttonSmallTopPadding),
    ),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        colors = colors,
        enabled = enabled,
        shape = DuckDuckGoTheme.shapes.small,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun DaxButtonLarge(
    onClick: () -> Unit,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    DaxButton(
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        enabled = enabled,
        height = dimensionResource(R.dimen.buttonLargeHeight),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(R.dimen.buttonLargeSidePadding),
            vertical = dimensionResource(R.dimen.buttonLargeTopPadding),
        ),
        content = content,
    )
}

@Composable
internal fun DaxButtonText(
    text: String,
    modifier: Modifier = Modifier
) {
    DaxTextPrimary(
        text = text,
        style = DuckDuckGoTheme.typography.button,
        modifier = modifier,
    )
}

@Composable
internal fun PreviewBox(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .background(DuckDuckGoTheme.colors.background)
            .padding(16.dp),
    ) {
        content()
    }
}
