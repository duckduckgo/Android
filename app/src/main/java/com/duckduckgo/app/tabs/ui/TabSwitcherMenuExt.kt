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

package com.duckduckgo.app.tabs.ui

import android.view.Menu
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.PopupTabsMenuBinding
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType.ARROW
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.BackButtonType.CLOSE
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.DynamicInterface
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutMode.GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutMode.HIDDEN
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutMode.LIST
import com.duckduckgo.mobile.android.R as commonR

fun Menu.createDynamicInterface(
    numSelectedTabs: Int,
    popupMenu: PopupTabsMenuBinding,
    toolbar: Toolbar,
    dynamicMenu: DynamicInterface,
) {
    popupMenu.selectAllMenuItem.isVisible = dynamicMenu.isSelectAllVisible
    popupMenu.deselectAllMenuItem.isVisible = dynamicMenu.isDeselectAllVisible
    popupMenu.selectionActionsDivider.isVisible = dynamicMenu.isSelectionActionsDividerVisible
    popupMenu.shareSelectedLinksMenuItem.isVisible = dynamicMenu.isShareSelectedLinksVisible
    popupMenu.bookmarkSelectedTabsMenuItem.isVisible = dynamicMenu.isBookmarkSelectedTabsVisible
    popupMenu.selectTabsDivider.isVisible = dynamicMenu.isSelectTabsDividerVisible
    popupMenu.selectTabsMenuItem.isVisible = dynamicMenu.isSelectTabsVisible
    popupMenu.closeSelectedTabsMenuItem.isVisible = dynamicMenu.isCloseSelectedTabsVisible
    popupMenu.closeOtherTabsMenuItem.isVisible = dynamicMenu.isCloseOtherTabsVisible
    popupMenu.closeAllTabsDivider.isVisible = dynamicMenu.isCloseAllTabsDividerVisible
    popupMenu.closeAllTabsMenuItem.isVisible = dynamicMenu.isCloseAllTabsVisible

    popupMenu.shareSelectedLinksMenuItem.apply {
        setPrimaryText(resources.getQuantityString(R.plurals.shareLinksMenuItem, numSelectedTabs, numSelectedTabs))
    }
    popupMenu.bookmarkSelectedTabsMenuItem.apply {
        setPrimaryText(resources.getQuantityString(R.plurals.bookmarkTabsMenuItem, numSelectedTabs, numSelectedTabs))
    }
    popupMenu.closeSelectedTabsMenuItem.apply {
        setPrimaryText(resources.getQuantityString(R.plurals.closeTabsMenuItem, numSelectedTabs, numSelectedTabs))
    }

    popupMenu.gridLayoutMenuItem.apply {
        when (dynamicMenu.layoutMenuMode) {
            GRID -> {
                setTrailingIconVisibility(false)
                isVisible = true
            }
            LIST -> {
                setTrailingIconVisibility(true)
                isVisible = true
            }
            HIDDEN -> isVisible = false
        }
    }

    popupMenu.listLayoutMenuItem.apply {
        when (dynamicMenu.layoutMenuMode) {
            GRID -> {
                setTrailingIconVisibility(true)
                isVisible = true
            }
            LIST -> {
                setTrailingIconVisibility(false)
                isVisible = true
            }
            HIDDEN -> isVisible = false
        }
    }

    toolbar.navigationIcon = when (dynamicMenu.backButtonType) {
        ARROW -> AppCompatResources.getDrawable(toolbar.context, commonR.drawable.ic_arrow_left_24)
        CLOSE -> AppCompatResources.getDrawable(toolbar.context, commonR.drawable.ic_close_24)
    }

    findItem(R.id.fireToolbarButton).isVisible = dynamicMenu.isFireButtonVisible
    findItem(R.id.duckAIToolbarButton).isVisible = dynamicMenu.isDuckAIButtonVisible
    findItem(R.id.newTabToolbarButton).isVisible = dynamicMenu.isNewTabButtonVisible
}
