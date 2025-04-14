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

import android.view.View.OnClickListener
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType

abstract class TabSwitcherAdapterBase : Adapter<ViewHolder>() {
    @VisibleForTesting
    abstract fun createCloseClickListener(
        bindingAdapterPosition: () -> Int,
        tabSwitcherListener: TabSwitcherListener,
    ): OnClickListener

    abstract fun updateData(updatedList: List<TabSwitcherItem>)
    abstract fun getTabSwitcherItem(position: Int): TabSwitcherItem?
    abstract fun getAdapterPositionForTab(tabId: String?): Int
    abstract fun onDraggingStarted()
    abstract fun onDraggingFinished()
    abstract fun onTabMoved(from: Int, to: Int)
    abstract fun onLayoutTypeChanged(layoutType: LayoutType)
    abstract fun setAnimationTileCloseClickListener(onClick: () -> Unit)
}
