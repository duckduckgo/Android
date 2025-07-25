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

package com.duckduckgo.app.tabs.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.duckduckgo.app.browser.R as BrowserR
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Selection
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TabSwitcherTopAppBar(
    onNavigationIconClicked: () -> Unit,
    mode: Mode,
    tabs: List<ComposeTabSwitcherItem>,  // I'm aware of the lint issue but strongSkipping is on
    onClearDataButtonClicked: () -> Unit,
    layoutType: LayoutType,
    onLayoutTypeToggled: () -> Unit,
    onMenuClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DuckDuckGoTheme.colors.background,
            scrolledContainerColor = DuckDuckGoTheme.colors.background,
            navigationIconContentColor = DuckDuckGoTheme.colors.primaryIcon,
            actionIconContentColor = DuckDuckGoTheme.colors.primaryIcon,
        ),
        navigationIcon = { NavigationIcon(onNavigationIconClicked, mode) },
        title = { TitleText(mode, tabs) },
        actions = { ActionsRow(mode, onClearDataButtonClicked, layoutType, onLayoutTypeToggled, onMenuClicked) },
        modifier = modifier,
    )
}

@Composable
private fun ActionsRow(
    mode: Mode,
    onClearDataButtonClicked: () -> Unit,
    layoutType: LayoutType,
    onLayoutTypeToggled: () -> Unit,
    onMenuClicked: () -> Unit,
) {
    if (mode is Normal) {
        IconButton(
            onClick = onClearDataButtonClicked,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_fire_24),
                contentDescription = stringResource(BrowserR.string.fireMenu),
            )
        }

        val layoutButtonPainter = when (layoutType) {
            GRID -> painterResource(R.drawable.ic_view_list_24)
            LIST -> painterResource(R.drawable.ic_view_grid_24)
        }

        val contentDescription = when (layoutType) {
            // TODO these should probably change to action terms e.g. "switch to list layout"
            GRID -> stringResource(BrowserR.string.tabSwitcherListViewMenu)
            LIST -> stringResource(BrowserR.string.tabSwitcherGridViewMenu)
        }

        IconButton(
            onClick = onLayoutTypeToggled,
        ) {
            Icon(
                painter = layoutButtonPainter,
                contentDescription = contentDescription,
            )
        }
    }

    IconButton(
        onClick = onMenuClicked,
    ) {
        // TODO needs a more appropriate content description
        Icon(
            painter = painterResource(id = R.drawable.ic_menu_vertical_24),
            contentDescription = stringResource(BrowserR.string.browserPopupMenu),
        )
    }
}

@Composable
private fun TitleText(
    mode: Mode,
    tabs: List<ComposeTabSwitcherItem>, // I'm aware of the lint issue but strongSkipping is on
    modifier: Modifier = Modifier,
) {
    var mode1 = mode
    val titleText = when (val mode = mode1) {
        is Normal -> pluralStringResource(
            id = BrowserR.plurals.tabSwitcherTitle,
            count = tabs.size,
            formatArgs = arrayOf(tabs.size),
        )

        is Selection -> {
            if (mode.selectedTabs.isEmpty()) {
                stringResource(BrowserR.string.selectTabsMenuItem)
            } else {
                stringResource(
                    id = BrowserR.string.tabSelectionTitle,
                    formatArgs = arrayOf(mode.selectedTabs.size),
                )
            }
        }
    }

    Text(text = titleText, style = DuckDuckGoTheme.typography.h2, modifier = modifier)
}

@Composable
private fun NavigationIcon(
    onNavigationIconClicked: () -> Unit,
    mode: Mode,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { onNavigationIconClicked() },
        modifier = modifier,
    ) {
        val iconPainter = when (mode) {
            is Normal -> painterResource(id = R.drawable.ic_arrow_left_24)
            is Selection -> painterResource(id = R.drawable.ic_close_24)
        }

        val contentDescription = when (mode) {
            is Normal -> stringResource(BrowserR.string.back)
            is Selection -> "Cancel selection mode" // TODO add a real string
        }

        Icon(
            painter = iconPainter,
            contentDescription = contentDescription,
        )
    }
}

