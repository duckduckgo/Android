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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerDefaults.layoutType
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size.Companion.Zero
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.NewTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.WebTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Normal
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherViewState.Mode.Selection
import com.duckduckgo.common.ui.compose.component.tabs.NewTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.NewTabListCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabListCard
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import kotlin.math.abs
import com.duckduckgo.app.browser.R as BrowserR
import com.duckduckgo.mobile.android.R

@Composable
fun TabSwitcherScreen(
    numberOfColumns: Int,
    modifier: Modifier = Modifier,
    viewModel: ComposeTabSwitcherViewModel = viewModel(),
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    TabSwitcherScreen(
        tabs = viewState.tabs,
        selectedTabIndex = viewState.selectedTabIndex,
        layoutType = viewState.layoutType,
        mode = viewState.mode,
        numberOfColumns = numberOfColumns,
        onNavigationIconClicked = viewModel::onNavigationIconClicked,
        onLayoutTypeToggled = viewModel::onLayoutTypeToggled,
        onMenuClicked = viewModel::onMenuClicked,
        onClearDataButtonClicked = viewModel::onClearDataButtonClicked,
        onTabClicked = viewModel::onTabClicked,
        onTabSwipedAway = viewModel::onTabSwipedAway,
        onCloseTabClicked = viewModel::onCloseTabClicked,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSwitcherScreen(
    tabs: List<ComposeTabSwitcherItem>, // I'm aware of the lint issue but strongSkipping is on
    selectedTabIndex: Int,
    layoutType: LayoutType,
    mode: Mode,
    numberOfColumns: Int,
    onNavigationIconClicked: () -> Unit,
    onLayoutTypeToggled: () -> Unit,
    onMenuClicked: () -> Unit,
    onClearDataButtonClicked: () -> Unit,
    onTabClicked: (tabId: String) -> Unit,
    onTabSwipedAway: (tabId: String) -> Unit,
    onCloseTabClicked: (tabId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyGridState = rememberLazyGridState()

    // Opted for Material3 Scaffold so we can use the new TopAppBar that has insets support
    Scaffold(
        containerColor = DuckDuckGoTheme.colors.background,
        topBar = {
            TabSwitcherTopAppBar(
                onNavigationIconClicked = onNavigationIconClicked,
                mode = mode,
                tabs = tabs,
                onClearDataButtonClicked = onClearDataButtonClicked,
                layoutType = layoutType,
                onLayoutTypeToggled = onLayoutTypeToggled,
                onMenuClicked = onMenuClicked,
            )
        },
        contentWindowInsets = WindowInsets.systemBars,
        modifier = modifier,
    ) { contentPadding ->
        TabSwitcherLazyVerticalGrid(
            lazyGridState = lazyGridState,
            numberOfColumns = numberOfColumns,
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            contentPadding = contentPadding,
            layoutType = layoutType,
            onTabClicked = onTabClicked,
            onTabSwipedAway = onTabSwipedAway,
            onCloseTabClicked = onCloseTabClicked,
        )
    }
}
