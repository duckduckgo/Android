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

import android.os.Message
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SwipingTabsFeature
import com.duckduckgo.app.tabs.model.TabEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class TabManager(
    private val activity: BrowserActivity,
    private val swipingTabsFeature: SwipingTabsFeature,
    private val onNewTabRequested: suspend (sourceTabId: String?) -> String,
) {
    companion object {
        private const val MAX_ACTIVE_TABS = 40
    }

    private val lastActiveTabs = TabList()
    private val supportFragmentManager = activity.supportFragmentManager
    private var openMessageInNewTabJob: Job? = null

    private var _currentTab: BrowserTabFragment? = null
    var currentTab: BrowserTabFragment?
        get() {
            return if (swipingTabsFeature.self().isEnabled()) {
                null
            } else {
                _currentTab
            }
        }
        set(value) {
            _currentTab = value
        }

    fun onSelectedTabChanged(tab: TabEntity?) {
        if (swipingTabsFeature.self().isEnabled()) {
            return
        } else if (tab != null) {
            selectTab(tab)
        }
    }

    fun onTabsUpdated(updatedTabs: List<TabEntity>) {
        if (swipingTabsFeature.self().isEnabled()) {
            return
        } else {
            clearStaleTabs(updatedTabs)
        }
    }

    fun cancelOpenMessageInNewTab() {
        openMessageInNewTabJob?.cancel()
    }

    private fun selectTab(tab: TabEntity?) {
        if (swipingTabsFeature.self().isEnabled()) {
            return
        }

        Timber.v("Select tab: $tab")

        if (tab == null) return

        if (tab.tabId == currentTab?.tabId) return

        lastActiveTabs.add(tab.tabId)

        val fragment = supportFragmentManager.findFragmentByTag(tab.tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tab.tabId, tab.url, tab.skipHome, activity.intent?.getBooleanExtra(BrowserActivity.LAUNCH_FROM_EXTERNAL_EXTRA, false) ?: false)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        currentTab?.let {
            transaction.hide(it)
        }
        transaction.show(fragment)
        transaction.commit()
        currentTab = fragment
    }

    private fun openNewTab(
        tabId: String,
        url: String? = null,
        skipHome: Boolean,
        isExternal: Boolean,
    ): BrowserTabFragment {
        Timber.i("Opening new tab, url: $url, tabId: $tabId")
        val fragment = BrowserTabFragment.newInstance(tabId, url, skipHome, isExternal)
        addOrReplaceNewTab(fragment, tabId)
        currentTab = fragment
        return fragment
    }

    private fun addOrReplaceNewTab(
        fragment: BrowserTabFragment,
        tabId: String,
    ) {
        if (supportFragmentManager.isStateSaved) {
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        val tab = currentTab
        if (tab == null) {
            transaction.replace(R.id.fragmentContainer, fragment, tabId)
        } else {
            transaction.hide(tab)
            transaction.add(R.id.fragmentContainer, fragment, tabId)
        }
        transaction.commit()
    }

    // TODO: Handle the FF case
    fun openMessageInNewTab(
        message: Message,
        sourceTabId: String?,
    ) {
        openMessageInNewTabJob = activity.lifecycleScope.launch {
            val tabId = onNewTabRequested(sourceTabId)
            val fragment = openNewTab(
                tabId = tabId,
                url = null,
                skipHome = false,
                isExternal = activity.intent?.getBooleanExtra(BrowserActivity.LAUNCH_FROM_EXTERNAL_EXTRA, false) ?: false,
            )
            fragment.messageFromPreviousTab = message
        }
    }

    private fun clearStaleTabs(updatedTabs: List<TabEntity>?) {
        if (swipingTabsFeature.self().isEnabled()) {
            return
        }

        if (updatedTabs == null) {
            return
        }

        val stale = supportFragmentManager
            .fragments.mapNotNull { it as? BrowserTabFragment }
            .filter { fragment -> updatedTabs.none { it.tabId == fragment.tabId } }

        if (stale.isNotEmpty()) {
            removeTabs(stale)
        }

        removeOldTabs()
    }

    private fun removeOldTabs() {
        val candidatesToRemove = lastActiveTabs.dropLast(MAX_ACTIVE_TABS)
        if (candidatesToRemove.isEmpty()) return

        val tabsToRemove = supportFragmentManager.fragments
            .mapNotNull { it as? BrowserTabFragment }
            .filter { candidatesToRemove.contains(it.tabId) }

        if (tabsToRemove.isNotEmpty()) {
            removeTabs(tabsToRemove)
        }
    }

    private fun removeTabs(fragments: List<BrowserTabFragment>) {
        val transaction = supportFragmentManager.beginTransaction()
        fragments.forEach {
            transaction.remove(it)
            lastActiveTabs.remove(it.tabId)
        }
        transaction.commit()
    }

    // Temporary class to keep track of latest visited tabs, keeping unique ids.
    private class TabList : ArrayList<String>() {
        override fun add(element: String): Boolean {
            if (this.contains(element)) {
                this.remove(element)
            }
            return super.add(element)
        }
    }
}
