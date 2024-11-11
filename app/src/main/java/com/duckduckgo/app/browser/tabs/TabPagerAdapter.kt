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

package com.duckduckgo.app.browser.tabs

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.tabs.model.TabEntity

class TabPagerAdapter(
    lifecycle: Lifecycle,
    private val fragmentManager: FragmentManager,
    private val moveToTabIndex: (Int, Boolean) -> Unit,
    private val getCurrentTabIndex: () -> Int,
    private val getSelectedTabId: () -> String?,
    private val getTabById: (String) -> TabEntity?,
    private val onTabSelected: (String) -> Unit,
    private val getOffScreenPageLimit: () -> Int,
    private val setOffScreenPageLimit: (Int) -> Unit,
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val tabIds = mutableListOf<String>()

    override fun getItemCount(): Int = tabIds.size

    override fun getItemId(position: Int) = tabIds[position].hashCode().toLong()

    override fun containsItem(itemId: Long) = tabIds.any { it.hashCode().toLong() == itemId }

    val currentFragment: BrowserTabFragment?
        get() = fragmentManager.fragments
            .filterIsInstance<BrowserTabFragment>()
            .firstOrNull { it.tabId == getSelectedTabId() }

    private val activeTabCount
        get() = fragmentManager.fragments
            .filterIsInstance<BrowserTabFragment>()
            .filter { it.isInitialized }.size

    override fun createFragment(position: Int): Fragment {
        increaseOffscreenTabLimitIfNeeded()

        val tab = getTabById(tabIds[position])!!
        return BrowserTabFragment.newInstance(tab.tabId, tab.url, tab.skipHome, false)
    }

    // init {
    //     lifecycleScope.launch {
    //         delay(5000)
    //         binding.fragmentPager.setCurrentItem(0, true)
    //         repeat(MAX_ACTIVE_TABS) {
    //             Timber.d("$$$ moving to #$it")
    //             binding.fragmentPager.setCurrentItem(it, true)
    //             delay(1000)
    //         }
    //     }
    // }

    fun onTabsUpdated(newTabs: List<String>) {
        if (tabIds != newTabs) {
            val diffUtil = PagerDiffUtil(tabIds, newTabs)
            val diff = DiffUtil.calculateDiff(diffUtil)
            tabIds.clear()
            tabIds.addAll(newTabs)
            diff.dispatchUpdatesTo(this)

            getSelectedTabId()?.let {
                onSelectedTabChanged(it)
            }
        }
    }

    fun onSelectedTabChanged(tabId: String) {
        val selectedTabIndex = tabIds.indexOfFirst { it == tabId }
        if (selectedTabIndex != -1 && selectedTabIndex != getCurrentTabIndex()) {
            moveToTabIndex(selectedTabIndex, false)
        }
    }

    fun onPageChanged(position: Int) {
        if (position < tabIds.size) {
            onTabSelected(tabIds[position])
        }
    }

    private fun increaseOffscreenTabLimitIfNeeded() {
        val offscreenPageLimit = getOffScreenPageLimit()
        if (activeTabCount >= offscreenPageLimit * 2 - 1 && activeTabCount < TabManager.MAX_ACTIVE_TABS) {
            setOffScreenPageLimit(offscreenPageLimit + 1)
        }
    }

    inner class PagerDiffUtil(
        private val oldList: List<String>,
        private val newList: List<String>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return true
        }
    }
}
