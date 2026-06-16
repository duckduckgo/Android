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

package com.duckduckgo.common.ui.compose.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed top app bar.
 *
 * @param title The title to display in the top app bar.
 * @param modifier Modifier for this top app bar.
 * @param shadow Whether to display a shadow below the top app bar.
 * @param navigationIcon Composable for the navigation icon, if any.
 * @param actions Composable for the action icons, if any.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215793353260352
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=20800-183716&m=dev
 */
@Composable
fun DaxTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    shadow: Boolean = false,
    navigationIcon: (@Composable DaxTopAppBarNavigationIconScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .then(if (shadow) Modifier.shadow(elevation = DaxTopAppBarDefaults.elevation) else Modifier)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .clipToBounds()
            .heightIn(min = DaxTopAppBarDefaults.height)
            .fillMaxWidth()
            .background(DaxTopAppBarDefaults.colors.containerColor)
            .padding(paddingValues = DaxTopAppBarDefaults.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            DaxTopAppBarNavigationIconScope.navigationIcon()
            Spacer(Modifier.width(DaxTopAppBarDefaults.spacing))
        }
        DaxText(
            text = title,
            modifier = Modifier.weight(1f),
            style = DaxTopAppBarDefaults.style,
            color = DaxTopAppBarDefaults.colors.titleColor,
            maxLines = 1,
        )
        actions(this)
    }
}

@Immutable
internal data class DaxTopAppBarColors(
    val containerColor: Color,
    val titleColor: Color,
)

internal object DaxTopAppBarDefaults {
    val colors: DaxTopAppBarColors
        @Composable
        get() = DaxTopAppBarColors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            titleColor = DuckDuckGoTheme.colors.text.primary,
        )

    val style: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.h2

    val contentPadding: PaddingValues = PaddingValues(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)

    val spacing: Dp = 24.dp

    val elevation: Dp = 2.dp

    val height: Dp = 56.dp
}

@PreviewLightDark
@Composable
private fun DaxTopAppBarDefaultPreview() {
    PreviewBox {
        DaxTopAppBar(
            title = "Title",
            navigationIcon = {
                Back { }
            },
            actions = {
                DaxIconButton(
                    onClick = {},
                    iconPainter = painterResource(R.drawable.ic_find_search_24),
                    contentDescription = "Search",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTopAppBarLongTitlePreview() {
    PreviewBox {
        DaxTopAppBar(
            title = "A very long title that should be truncated with an ellipsis at the end",
            navigationIcon = {
                Close { }
            },
            shadow = true,
            actions = {
                DaxIconButton(
                    onClick = {},
                    iconPainter = painterResource(R.drawable.ic_find_search_24),
                    contentDescription = "Search",
                )
            },
        )
    }
}
