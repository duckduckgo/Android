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
import com.duckduckgo.app.browser.tabs.adapter.TabPagerAdapter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import dagger.android.DaggerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface TabManager : CoroutineScope {
    companion object {
        const val MAX_ACTIVE_TABS = 20
    }

    val keepSingleTab: Boolean
    val tabOperationManager: TabOperationManager

    val currentTab: BrowserTabFragment?
    val tabPagerAdapter: TabPagerAdapter

    fun onTabPageSwiped(newPosition: Int)
    fun openMessageInNewTab(message: Message, sourceTabId: String?)
    fun clearTabsInMemory()
    fun onCleanup()
    fun onTabsUpdated(updatedTabIds: List<String>)

    fun onSelectedTabChanged(tabId: String) = tabOperationManager.onSelectedTabChanged(tabId)
    fun onTabsChanged(updatedTabIds: List<String>) = launch { tabOperationManager.onTabsChanged(updatedTabIds) }
    fun switchToTab(tabId: String) = launch { tabOperationManager.selectTab(tabId) }
    fun launchNewTab(query: String? = null, sourceTabId: String? = null, skipHome: Boolean = false) = launch { tabOperationManager.openNewTab() }
}

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class DefaultTabManager @Inject constructor(
    activity: DaggerActivity,
    override val tabOperationManager: TabOperationManager,
) : TabManager {
    private val browserActivity = activity as BrowserActivity
    private val supportFragmentManager = activity.supportFragmentManager
    private val coroutineScope = browserActivity.lifecycleScope

    private var openMessageInNewTabJob: Job? = null

    override val keepSingleTab: Boolean
        get() = !browserActivity.tabPager.isUserInputEnabled

    override val tabPagerAdapter by lazy {
        TabPagerAdapter(
            fragmentManager = supportFragmentManager,
            lifecycleOwner = browserActivity,
            activityIntent = browserActivity.intent,
            getSelectedTabId = { tabOperationManager.getSelectedTabId() },
            getTabById = ::getTabById,
            requestAndWaitForNewTab = ::requestAndWaitForNewTab,
        )
    }

    init {
        tabOperationManager.setTabManager(this)
    }

    override fun onTabPageSwiped(newPosition: Int) {
        val tabId = tabPagerAdapter.getTabIdAtPosition(newPosition)
        if (tabId != null) {
            switchToTab(tabId)
        }
    }

    override val currentTab: BrowserTabFragment?
        get() = tabPagerAdapter.currentFragment

    override fun openMessageInNewTab(message: Message, sourceTabId: String?) {
        openMessageInNewTabJob = coroutineScope.launch {
            tabPagerAdapter.setMessageForNewFragment(message)
            tabOperationManager.openNewTab(sourceTabId)
        }
    }

    override fun clearTabsInMemory() {
        tabPagerAdapter.clearFragments()
    }

    override fun onCleanup() {
        openMessageInNewTabJob?.cancel()
    }

    override fun onTabsUpdated(updatedTabIds: List<String>) {
        tabPagerAdapter.onTabsUpdated(updatedTabIds)
    }

    private fun getTabById(tabId: String): TabEntity? = runBlocking {
        return@runBlocking tabOperationManager.getTabById(tabId)
    }

    private fun requestAndWaitForNewTab(): TabEntity = runBlocking {
        return@runBlocking tabOperationManager.requestAndWaitForNewTab()
    }

    override val coroutineContext: CoroutineContext = activity.lifecycleScope.coroutineContext
}
