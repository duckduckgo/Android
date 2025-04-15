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
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.FabType
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutButtonType.GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutButtonType.HIDDEN
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.SelectionViewState.LayoutButtonType.LIST
import com.duckduckgo.mobile.android.R as commonR
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

fun Menu.createDynamicInterface(
    numSelectedTabs: Int,
    popupMenu: PopupTabsMenuBinding,
    mainFab: ExtendedFloatingActionButton,
    aiFab: ExtendedFloatingActionButton,
    toolbar: Toolbar,
    dynamicMenu: DynamicInterface,
) {
    popupMenu.newTabMenuItem.isVisible = dynamicMenu.isNewTabVisible
    popupMenu.duckChatMenuItem.isVisible = dynamicMenu.isDuckChatVisible
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

    mainFab.apply {
        if (dynamicMenu.isMainFabVisible) {
            when (dynamicMenu.mainFabType) {
                FabType.NEW_TAB -> {
                    text = resources.getString(R.string.newTabMenuItem)
                    icon = AppCompatResources.getDrawable(context, commonR.drawable.ic_add_24)
                }
                FabType.CLOSE_TABS -> {
                    text = resources.getQuantityString(R.plurals.closeTabsMenuItem, numSelectedTabs, numSelectedTabs)
                    icon = AppCompatResources.getDrawable(context, commonR.drawable.ic_close_24)
                }
            }

            show()
            extend()
        } else {
            hide()
        }
    }

    if (dynamicMenu.isAIFabVisible) {
        aiFab.show()
    } else {
        aiFab.hide()
    }

    toolbar.navigationIcon = when (dynamicMenu.backButtonType) {
        ARROW -> AppCompatResources.getDrawable(toolbar.context, commonR.drawable.ic_arrow_left_24)
        CLOSE -> AppCompatResources.getDrawable(toolbar.context, commonR.drawable.ic_close_24)
    }

    findItem(R.id.layoutTypeMenuItem).apply {
        when (dynamicMenu.layoutButtonType) {
            GRID -> {
                setIcon(R.drawable.ic_grid_view_24)
                title = toolbar.resources.getString(R.string.tabSwitcherGridViewMenu)
                isVisible = true
            }
            LIST -> {
                setIcon(R.drawable.ic_list_view_24)
                title = toolbar.resources.getString(R.string.tabSwitcherListViewMenu)
                isVisible = true
            }
            HIDDEN -> isVisible = false
        }
    }

    findItem(R.id.popupMenuItem).isEnabled = dynamicMenu.isMoreMenuItemEnabled
    findItem(R.id.fireMenuItem).isVisible = dynamicMenu.isFireButtonVisible
}
