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
import com.duckduckgo.app.browser.BrowserActivity.Companion.LAUNCH_FROM_EXTERNAL_EXTRA
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SwipingTabsFeatureProvider
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import dagger.android.DaggerActivity
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class RealTabManager @Inject constructor(
    activity: DaggerActivity,
    private val swipingTabsFeature: SwipingTabsFeatureProvider,
) : TabManager {
    companion object {
        private const val MAX_ACTIVE_TABS = 40
    }

    private val browserActivity = activity as BrowserActivity
    private val lastActiveTabs = TabList()
    private val supportFragmentManager = activity.supportFragmentManager
    private var openMessageInNewTabJob: Job? = null

    private var _currentTab: BrowserTabFragment? = null
    override var currentTab: BrowserTabFragment?
        get() {
            return if (swipingTabsFeature.isEnabled) {
                null
            } else {
                _currentTab
            }
        }
        set(value) {
            _currentTab = value
        }

    override fun onSelectedTabChanged(tab: TabEntity?) {
        if (swipingTabsFeature.isEnabled) {
            return
        } else if (tab != null) {
            selectTab(tab)
        }
    }

    override fun onTabsUpdated(updatedTabs: List<TabEntity>) {
        if (swipingTabsFeature.isEnabled) {
            return
        } else {
            clearStaleTabs(updatedTabs)
        }
        browserActivity.lifecycleScope.launch {
            browserActivity.viewModel.onTabsUpdated(updatedTabs)
        }
    }

    override fun openMessageInNewTab(
        message: Message,
        sourceTabId: String?,
    ) {
        openMessageInNewTabJob = browserActivity.lifecycleScope.launch {
            val tabId = browserActivity.viewModel.onNewTabRequested(sourceTabId)
            val fragment = openNewTab(
                tabId = tabId,
                url = null,
                skipHome = false,
                isExternal = browserActivity.intent?.getBooleanExtra(
                    BrowserActivity.LAUNCH_FROM_EXTERNAL_EXTRA,
                    false,
                ) ?: false,
            )
            fragment.messageFromPreviousTab = message
        }
    }

    override fun openExistingTab(tabId: String) {
        browserActivity.lifecycleScope.launch {
            browserActivity.viewModel.onTabSelected(tabId)
        }
    }

    override fun launchNewTab() {
        browserActivity.lifecycleScope.launch { browserActivity.viewModel.onNewTabRequested() }
    }

    override fun openQueryInNewTab(
        query: String,
        sourceTabId: String?,
    ) {
        browserActivity.lifecycleScope.launch {
            browserActivity.viewModel.onOpenInNewTabRequested(
                query = query,
                sourceTabId = sourceTabId,
            )
        }
    }

    override fun onCleanup() {
        openMessageInNewTabJob?.cancel()
    }

    private fun selectTab(tab: TabEntity?) {
        if (swipingTabsFeature.isEnabled) {
            return
        }

        Timber.v("Select tab: $tab")

        if (tab == null) return

        if (tab.tabId == currentTab?.tabId) return

        lastActiveTabs.add(tab.tabId)

        browserActivity.viewModel.onTabActivated(tab.tabId)

        val fragment = supportFragmentManager.findFragmentByTag(tab.tabId) as? BrowserTabFragment
        if (fragment == null) {
            openNewTab(tab.tabId, tab.url, tab.skipHome, browserActivity.intent?.getBooleanExtra(LAUNCH_FROM_EXTERNAL_EXTRA, false) == true)
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

    private fun clearStaleTabs(updatedTabs: List<TabEntity>?) {
        if (swipingTabsFeature.isEnabled) {
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
