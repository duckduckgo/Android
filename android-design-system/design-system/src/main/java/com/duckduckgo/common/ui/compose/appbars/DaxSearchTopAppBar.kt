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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewFontScale
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
 * DuckDuckGo themed top app bar with a search mode.
 *
 * The bar is in exactly one mode at a time: title mode (title + navigation icon + actions) or
 * search mode (a search input). The two modes crossfade; the bar is never composed twice at once.
 * On entering search mode the input field takes focus and raises the keyboard.
 *
 * Search state is caller-owned; this bar does not intercept the system back button. Callers that
 * want system back to exit search mode should add their own [androidx.activity.compose.BackHandler]
 * gated on the search-active state.
 *
 * @param title The title to display in title mode.
 * @param searchState The state of the search text field.
 * @param modifier Modifier for this top app bar.
 * @param shadow Whether to display a shadow below the top app bar.
 * @param searchActive Whether the bar is in search mode.
 * @param searchPlaceholder The placeholder text for the search field, if any.
 * @param onSearchBack Callback when the back button is pressed in search mode.
 * @param onSearch Callback when a search is submitted.
 * @param navigationIcon The navigation icon [com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarNavigationIcon] to display in title mode,
 * or null for none.
 * @param actions Composable for the action icons shown in title mode, if any.
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
    navigationIcon: DaxTopAppBarNavigationIcon? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AnimatedContent(
        targetState = searchActive,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
        label = "DaxSearchTopAppBarMode",
    ) { active ->
        if (active) {
            SearchModeBar(
                searchState = searchState,
                searchPlaceholder = searchPlaceholder,
                shadow = shadow,
                onSearchBack = onSearchBack,
                onSearch = onSearch,
            )
        } else {
            DaxTopAppBar(
                title = title,
                shadow = shadow,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        }
    }
}

@Composable
private fun SearchModeBar(
    searchState: TextFieldState,
    searchPlaceholder: String?,
    shadow: Boolean,
    onSearchBack: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inInspectionMode = LocalInspectionMode.current

    LaunchedEffect(Unit) {
        if (!inInspectionMode) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .then(if (shadow) Modifier.shadow(elevation = DaxTopAppBarDefaults.Elevation) else Modifier)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .clipToBounds()
            .heightIn(min = DaxTopAppBarDefaults.Height)
            .fillMaxWidth()
            .background(DaxTopAppBarDefaults.colors.containerColor)
            .padding(DaxSearchTopAppBarDefaults.Margin),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = DaxSearchTopAppBarDefaults.shape,
            color = DaxSearchTopAppBarDefaults.colors.surfaceColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(DaxSearchTopAppBarDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DaxIconButton(
                    onClick = onSearchBack,
                    iconPainter = painterResource(R.drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.dax_top_app_bar_exit_search_content_description),
                )
                Spacer(Modifier.width(DaxSearchTopAppBarDefaults.Spacing))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        state = searchState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = DaxSearchTopAppBarDefaults.queryStyle.asTextStyle.copy(color = DaxSearchTopAppBarDefaults.colors.queryColor),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = KeyboardActionHandler {
                            keyboardController?.hide()
                            onSearch(searchState.text.toString())
                        },
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

    val Margin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

    val ContentPadding: PaddingValues = PaddingValues(start = 4.dp, end = 8.dp)

    val Spacing: Dp = 16.dp
}

@PreviewLightDark
@Composable
private fun DaxSearchTopAppBarEmptyPreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = true,
            searchState = rememberTextFieldState(),
            searchPlaceholder = "Search…",
            navigationIcon = DaxTopAppBarNavigationIcon.Back { },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSearchTopAppBarTypedPreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = true,
            searchState = rememberTextFieldState("query"),
            searchPlaceholder = "Search…",
            navigationIcon = DaxTopAppBarNavigationIcon.Close { },
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSearchTopAppBarTitleModePreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = false,
            searchState = rememberTextFieldState(),
            searchPlaceholder = "Search…",
            navigationIcon = DaxTopAppBarNavigationIcon.Back { },
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

@PreviewFontScale
@Composable
private fun DaxSearchTopAppBarFontScalePreview() {
    PreviewBox {
        DaxSearchTopAppBar(
            title = "Title",
            searchActive = true,
            searchState = rememberTextFieldState("query"),
            searchPlaceholder = "Search…",
            navigationIcon = DaxTopAppBarNavigationIcon.Back { },
        )
    }
}
