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
import android.os.Bundle
import android.os.Message
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.tabs.TabManager.TabModel

class TabPagerAdapter(
    private val activity: BrowserActivity,
) : FragmentStateAdapter(activity) {
    private val tabs = mutableListOf<TabModel>()
    private var messageForNewFragment: Message? = null

    var currentTabIndex = -1
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            // if the current tab index is -1 and the set value 0, it means the first tab is really selected
            // and we need to notify the adapter to create the first fragment
            if (field == -1 && value == 0) {
                notifyItemChanged(0)
            }
            field = value
        }

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
        val isExternal = activity.intent?.getBooleanExtra(BrowserActivity.LAUNCH_FROM_EXTERNAL_EXTRA, false) == true

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

    // This method prevents the creation of a tab fragment for the first tab when we don't know the current tab index yet
    override fun shouldPlaceFragmentInViewHolder(position: Int): Boolean {
        return currentTabIndex != -1 || position != 0
    }

    fun restore(state: Bundle) {
        // state is only useful when there are fragments to restore (also avoids a crash)
        if (activity.supportFragmentManager.fragments.isNotEmpty()) {
            restoreState(state)
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

            var wereTabsRemoved = false
            val updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    this@TabPagerAdapter.notifyItemRangeInserted(position, count)
                }

                override fun onRemoved(position: Int, count: Int) {
                    this@TabPagerAdapter.notifyItemRangeRemoved(position, count)
                    wereTabsRemoved = true
                }

                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    this@TabPagerAdapter.notifyItemMoved(fromPosition, toPosition)
                }

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    this@TabPagerAdapter.notifyItemRangeChanged(position, count, payload)
                }
            }
            diff.dispatchUpdatesTo(updateCallback)

            if (wereTabsRemoved) {
                cleanupRemovedItems()
            }
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
