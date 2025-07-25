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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Size.Companion.Zero
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.toSize
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.NewTab
import com.duckduckgo.app.tabs.ui.compose.ComposeTabSwitcherItem.WebTab
import com.duckduckgo.common.ui.compose.component.tabs.NewTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.NewTabListCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabGridCard
import com.duckduckgo.common.ui.compose.component.tabs.WebTabListCard
import com.duckduckgo.mobile.android.R
import kotlin.math.abs

@Composable
fun TabSwitcherLazyVerticalGrid(
    lazyGridState: LazyGridState,
    numberOfColumns: Int,
    tabs: List<ComposeTabSwitcherItem>,
    selectedTabIndex: Int,
    contentPadding: PaddingValues,
    layoutType: LayoutType,
    onTabClicked: (tabId: String) -> Unit,
    onTabSwipedAway: (tabId: String) -> Unit,
    onCloseTabClicked: (tabId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(key1 = tabs.size) {
        if (tabs.isNotEmpty()) {
            lazyGridState.scrollToItem(selectedTabIndex)
        }
    }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(numberOfColumns),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_4)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_4)),
        contentPadding = contentPadding,
        modifier = modifier.padding(dimensionResource(R.dimen.keyline_4)),
    ) {
        items(
            items = tabs,
            key = { it.id },
            contentType = { getContentType(layoutType, it) },
        ) { tabSwitcherItem ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onTabSwipedAway(tabSwitcherItem.id)
                        true
                    } else {
                        false
                    }
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.75f },
            )

            var componentSize by remember { mutableStateOf(Zero) }

            val alpha = calculateItemAlpha(componentSize, dismissState)

            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(), // this is buggy with the selected item outline when loading
                enableDismissFromStartToEnd = dismissState.currentValue == SwipeToDismissBoxValue.Settled,
                enableDismissFromEndToStart = dismissState.currentValue == SwipeToDismissBoxValue.Settled,
                backgroundContent = {},
            ) {
                val itemModifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        this.compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .onSizeChanged {
                        componentSize = it.toSize()
                    }

                when (layoutType) {
                    GRID -> MapToGridItem(
                        onTabClicked = onTabClicked,
                        onCloseTabClicked = onCloseTabClicked,
                        tabSwitcherItem = tabSwitcherItem,
                        modifier = itemModifier,
                    )

                    LIST -> MapToListItem(
                        onTabClicked = onTabClicked,
                        tabSwitcherItem = tabSwitcherItem,
                        onCloseTabClicked = onCloseTabClicked,
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}

private fun calculateItemAlpha(
    componentSize: Size,
    dismissState: SwipeToDismissBoxState
): Float = if (componentSize.width > 0) {
    val swipeProgress = abs(dismissState.requireOffset()) / componentSize.width
    (1f - swipeProgress).coerceIn(0f, 1f)
} else {
    1f
}

private fun getContentType(
    layoutType: LayoutType,
    item: ComposeTabSwitcherItem
): String = when (layoutType) {
    GRID -> getGridItemId(item)
    LIST -> getListItemId(item)
}

private fun getGridItemId(item: ComposeTabSwitcherItem): String = when (item) {
    is NewTab -> "newTabGrid"
    is WebTab -> "webTabGrid"
}

private fun getListItemId(item: ComposeTabSwitcherItem): String = when (item) {
    is NewTab -> "newTabList"
    is WebTab -> "webTabList"
}

@Composable
private fun MapToGridItem(
    onTabClicked: (tabId: String) -> Unit,
    onCloseTabClicked: (tabId: String) -> Unit,
    tabSwitcherItem: ComposeTabSwitcherItem,
    modifier: Modifier = Modifier,
) {
    when (tabSwitcherItem) {
        is NewTab -> {
            NewTabGridCard(
                isSelected = tabSwitcherItem.isCurrentTab,
                onTabClick = {
                    onTabClicked(tabSwitcherItem.id)
                },
                onCloseTabClick = {
                    onCloseTabClicked(tabSwitcherItem.id)
                },
                modifier = modifier,
            )
        }

        is WebTab -> {
            WebTabGridCard(
                title = tabSwitcherItem.title,
                faviconModel = tabSwitcherItem.faviconBitmap,
                isUnreadIndicatorVisible = tabSwitcherItem.isUnreadIndicatorVisible,
                tabPreviewFile = tabSwitcherItem.tabPreviewFilePath,
                isCurrentTab = tabSwitcherItem.isCurrentTab,
                onTabClick = {
                    onTabClicked(tabSwitcherItem.id)
                },
                onCloseTabClick = {
                    onCloseTabClicked(tabSwitcherItem.id)
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MapToListItem(
    tabSwitcherItem: ComposeTabSwitcherItem,
    onTabClicked: (tabId: String) -> Unit,
    onCloseTabClicked: (tabId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (tabSwitcherItem) {
        is NewTab -> {
            NewTabListCard(
                isCurrentTab = tabSwitcherItem.isCurrentTab,
                onTabClick = {
                    onTabClicked(tabSwitcherItem.id)
                },
                onCloseTabClick = {
                    onCloseTabClicked(tabSwitcherItem.id)
                },
                modifier = modifier,
            )
        }

        is WebTab -> {
            WebTabListCard(
                title = tabSwitcherItem.title,
                url = tabSwitcherItem.tabEntity.url ?: "",
                faviconModel = tabSwitcherItem.faviconBitmap,
                isUnreadIndicatorVisible = tabSwitcherItem.isUnreadIndicatorVisible,
                isCurrentTab = tabSwitcherItem.isCurrentTab,
                onTabClick = {
                    onTabClicked(tabSwitcherItem.id)
                },
                onCloseTabClick = {
                    onCloseTabClicked(tabSwitcherItem.id)
                },
                modifier = modifier,
            )
        }
    }
}
