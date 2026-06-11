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

package com.duckduckgo.common.ui.compose.divider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system composable vertical divider.
 *
 * Wraps Material3 [VerticalDivider] with DuckDuckGo theme colors matching the xml [com.duckduckgo.common.ui.view.divider.VerticalDivider].
 * Callers control surrounding spacing via [modifier].
 *
 * The parent layout must impose a height (e.g. `Modifier.height(...)` or a
 * `Row` with bounded height); the divider fills the available height.
 *
 * Common usages:
 * - `DaxVerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))` to
 *   match the XML divider's `app:defaultPadding="true"` behavior.
 *
 * @param modifier the [Modifier] to apply
 * @param thickness the thickness of the divider line
 * @param color the color of the divider line
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215500875198340?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?m=dev&node-id=6036-11645
 */
@Composable
fun DaxVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DaxVerticalDividerDefaults.Thickness,
    color: Color = DaxVerticalDividerDefaults.color,
) {
    VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
    )
}

object DaxVerticalDividerDefaults {
    val Thickness: Dp = 1.dp

    val color: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.backgrounds.container
}

@PreviewLightDark
@Composable
private fun DaxVerticalDividerPreview() {
    PreviewBox {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DaxVerticalDivider()
            DaxVerticalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaxVerticalDivider(
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
