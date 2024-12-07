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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Message
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.tabs.adapter.FragmentStateAdapter
import com.duckduckgo.app.tabs.model.TabEntity
import timber.log.Timber

class TabPagerAdapter(
    lifecycleOwner: LifecycleOwner,
    private val fragmentManager: FragmentManager,
    private val activityIntent: Intent?,
    private val moveToTabIndex: (Int) -> Unit,
    private val getTabById: (String) -> TabEntity?,
    private val requestNewTab: () -> TabEntity,
    private val getSelectedTabId: () -> String?,
    private val onTabSelected: (String) -> Unit,
    private val getOffScreenPageLimit: () -> Int,
    private val setOffScreenPageLimit: (Int) -> Unit,
) : FragmentStateAdapter(fragmentManager, lifecycleOwner.lifecycle) {
    private val tabIds = mutableListOf<String>()
    private var messageForNewFragment: Message? = null

    override fun getItemCount() = tabIds.size

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
        Timber.d("### TabPagerAdapter.createFragment: $position")

        val tab = getTabById(tabIds[position]) ?: requestNewTab()
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
    fun onTabsUpdated(newTabs: List<String>) {
        increaseOffscreenTabLimitIfNeeded()

        val diff = DiffUtil.calculateDiff(PagerDiffUtil(tabIds, newTabs))
        diff.dispatchUpdatesTo(this)
        tabIds.clear()
        tabIds.addAll(newTabs)

        onSelectedTabChanged(getSelectedTabId() ?: tabIds.first())
    }

    fun onSelectedTabChanged(tabId: String) {
        val selectedTabIndex = tabIds.indexOfFirst { it == tabId }
        if (selectedTabIndex != -1) {
            moveToTabIndex(selectedTabIndex)
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
            return areItemsTheSame(oldItemPosition, newItemPosition)
        }
    }
}
