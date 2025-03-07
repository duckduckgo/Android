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
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.PopupTabsMenuBinding
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.DynamicInterface
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.ViewState.FabType
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

fun Menu.createDynamicInterface(
    numSelectedTabs: Int,
    popupMenu: PopupTabsMenuBinding,
    fab: ExtendedFloatingActionButton,
    dynamicMenu: DynamicInterface,
): MenuItem {
    findItem(R.id.fireMenuItem).isVisible = dynamicMenu.isFireButtonVisible
    val layoutButton = findItem(R.id.layoutTypeMenuItem).apply {
        isVisible = dynamicMenu.isLayoutTypeButtonVisible
    }

    popupMenu.newTabMenuItem.isVisible = dynamicMenu.isNewTabVisible
    popupMenu.selectAllMenuItem.isVisible = dynamicMenu.isSelectAllVisible
    // popupMenu.deselectAllMenuItem.isVisible = dynamicMenu.isDeselectAllVisible
    popupMenu.selectionActionsDivider.isVisible = dynamicMenu.isSelectionActionsDividerVisible
    popupMenu.shareSelectedLinksMenuItem.isVisible = dynamicMenu.isShareSelectedLinksVisible
    popupMenu.bookmarkSelectedTabsMenuItem.isVisible = dynamicMenu.isBookmarkSelectedTabsVisible
    popupMenu.selectTabsDivider.isVisible = dynamicMenu.isSelectTabsDividerVisible
    popupMenu.selectTabsMenuItem.isVisible = dynamicMenu.isSelectTabsVisible
    popupMenu.closeSelectedTabsMenuItem.isVisible = dynamicMenu.isCloseSelectedTabsVisible
    popupMenu.closeOtherTabsMenuItem.isVisible = dynamicMenu.isCloseOtherTabsVisible
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

    fab.apply {
        isVisible = dynamicMenu.isFabVisible
        when (dynamicMenu.fabType) {
            FabType.NEW_TAB -> {
                text = resources.getString(R.string.newTabMenuItem)
                icon = AppCompatResources.getDrawable(context, CommonR.drawable.ic_add_24)
            }
            FabType.CLOSE_TABS -> {
                text = resources.getQuantityString(R.plurals.closeTabsMenuItem, numSelectedTabs, numSelectedTabs)
                icon = AppCompatResources.getDrawable(context, CommonR.drawable.ic_close_24)
            }
        }
    }

    return layoutButton
}
