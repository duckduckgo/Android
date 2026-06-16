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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo themed top app bar with search functionality.
 *
 * @param title The title to display in the top app bar.
 * @param searchState The state of the search text field.
 * @param modifier Modifier for this top app bar.
 * @param shadow Whether to display a shadow below the top app bar.
 * @param searchActive Whether the search bar is active and visible.
 * @param searchPlaceholder The placeholder text for the search field, if any.
 * @param onSearchBack Callback when the back button is pressed in search mode.
 * @param onSearch Callback when a search is submitted.
 * @param navigationIcon Composable for the navigation icon, if any.
 * @param actions Composable for the action icons, if any.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1215793353260352
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=20800-183716&m=dev
 */
@Composable
fun DaxSearchTopAppBar(
    title: String,
    searchState: TextFieldState,
    modifier: Modifier = Modifier,
    shadow: Boolean = false,
    searchActive: Boolean = false,
    searchPlaceholder: String? = null,
    onSearchBack: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    navigationIcon: (@Composable DaxTopAppBarNavigationIconScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        DaxTopAppBar(
            title = title,
            shadow = shadow,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        AnimatedVisibility(
            visible = searchActive,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            SearchBarContent(
                searchState = searchState,
                searchPlaceholder = searchPlaceholder,
                onSearchBack = onSearchBack,
                onSearch = onSearch,
                modifier = Modifier
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .fillMaxWidth()
                    .background(DaxTopAppBarDefaults.colors.containerColor),
            )
        }
    }
}

@Composable
private fun SearchBarContent(
    searchState: TextFieldState,
    searchPlaceholder: String?,
    onSearchBack: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(DaxSearchTopAppBarDefaults.margin)) {
        Surface(
            shape = DaxSearchTopAppBarDefaults.shape,
            color = DaxSearchTopAppBarDefaults.colors.surfaceColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(DaxSearchTopAppBarDefaults.contentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DaxIconButton(
                    onClick = onSearchBack,
                    iconPainter = painterResource(R.drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.dax_top_app_bar_exit_search_content_description),
                )
                Spacer(Modifier.width(DaxSearchTopAppBarDefaults.spacing))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        state = searchState,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = DaxSearchTopAppBarDefaults.queryStyle.asTextStyle.copy(color = DaxSearchTopAppBarDefaults.colors.queryColor),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = KeyboardActionHandler { _ -> onSearch(searchState.text.toString()) },
                        cursorBrush = SolidColor(DaxSearchTopAppBarDefaults.colors.cursorColor),
                    )
                    if (searchState.text.isEmpty() && searchPlaceholder != null) {
                        DaxText(
                            text = searchPlaceholder,
                            style = DaxSearchTopAppBarDefaults.placeholderStyle,
                            color = DaxSearchTopAppBarDefaults.colors.placeholderColor,
                            maxLines = 1,
                        )
                    }
                }
                if (searchState.text.isNotEmpty()) {
                    DaxIconButton(
                        onClick = { searchState.clearText() },
                        iconPainter = painterResource(R.drawable.ic_close_24),
                        contentDescription = stringResource(R.string.dax_top_app_bar_clear_search_content_description),
                    )
                }
            }
        }
    }
}

@Immutable
internal data class DaxSearchTopAppBarColors(
    val surfaceColor: Color,
    val placeholderColor: Color,
    val queryColor: Color,
    val cursorColor: Color,
)

internal object DaxSearchTopAppBarDefaults {
    val colors: DaxSearchTopAppBarColors
        @Composable
        get() = DaxSearchTopAppBarColors(
            surfaceColor = DuckDuckGoTheme.colors.backgrounds.container,
            placeholderColor = DuckDuckGoTheme.colors.text.secondary,
            queryColor = DuckDuckGoTheme.colors.text.primary,
            cursorColor = DuckDuckGoTheme.colors.text.primary,
        )

    val placeholderStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body1

    val queryStyle: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body1

    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.large

    val margin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

    val contentPadding: PaddingValues = PaddingValues(start = 4.dp, end = 8.dp)

    val spacing: Dp = 16.dp
}

@PreviewLightDark
@Composable
private fun DaxTopAppBarSearchEmptyPreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = true,
            searchState = rememberTextFieldState(),
            searchPlaceholder = "Search…",
            navigationIcon = {
                Back { }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxTopAppBarSearchTypedPreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = true,
            searchState = rememberTextFieldState("query"),
            searchPlaceholder = "Search…",
            navigationIcon = {
                Close { }
            },
        )
    }
}
