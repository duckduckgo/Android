/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.tabs.adapter

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Message
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.tabs.TabManager.TabModel
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider

class TabPagerAdapter(
    lifecycleOwner: LifecycleOwner,
    fragmentManager: FragmentManager,
    private val activityIntent: Intent?,
    swipingTabsFeature: SwipingTabsFeatureProvider,
) : FragmentStateAdapter(fragmentManager, lifecycleOwner.lifecycle, swipingTabsFeature) {
    private val tabs = mutableListOf<TabModel>()
    private var messageForNewFragment: Message? = null

    override fun getItemCount() = tabs.size

    override fun getItemId(position: Int): Long {
        return if (position >= 0 && position < tabs.size) {
            tabs[position].tabId.hashCode().toLong()
        } else {
            RecyclerView.NO_ID
        }
    }

    override fun containsItem(itemId: Long) = tabs.any { it.tabId.hashCode().toLong() == itemId }

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val isExternal = activityIntent?.getBooleanExtra(BrowserActivity.LAUNCH_FROM_EXTERNAL_EXTRA, false) == true

        return if (messageForNewFragment != null) {
            val message = messageForNewFragment
            messageForNewFragment = null
            return BrowserTabFragment.newInstance(tab.tabId, null, false, isExternal).apply {
                this.messageFromPreviousTab = message
            }
        } else {
            BrowserTabFragment.newInstance(tab.tabId, tab.url, tab.skipHome, isExternal)
        }
    }

    fun setMessageForNewFragment(message: Message) {
        messageForNewFragment = message
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onTabsUpdated(newTabs: List<TabModel>) {
        if (tabs.map { it.tabId } != newTabs.map { it.tabId }) {
            // we only want to notify the adapter if the tab IDs change
            val diff = DiffUtil.calculateDiff(PagerDiffUtil(tabs, newTabs))
            tabs.clear()
            tabs.addAll(newTabs)
            diff.dispatchUpdatesTo(this)
        } else {
            // the state of tabs is managed separately, so we don't need to notify the adapter, but we need URL and skipHome to create new fragments
            tabs.clear()
            tabs.addAll(newTabs)
        }
    }

    fun getTabIdAtPosition(position: Int): String? {
        return if (position < tabs.size) {
            tabs[position].tabId
        } else {
            null
        }
    }

    inner class PagerDiffUtil(
        private val oldList: List<TabModel>,
        private val newList: List<TabModel>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].tabId == newList[newItemPosition].tabId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return areItemsTheSame(oldItemPosition, newItemPosition)
        }
    }
}
