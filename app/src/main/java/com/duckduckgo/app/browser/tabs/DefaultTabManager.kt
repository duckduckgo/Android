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
import com.duckduckgo.app.browser.SkipUrlConversionOnNewTabFeature
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.tabs.adapter.TabPagerAdapter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import dagger.android.DaggerActivity
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class DefaultTabManager @Inject constructor(
    activity: DaggerActivity,
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val skipUrlConversionOnNewTabFeature: SkipUrlConversionOnNewTabFeature,
) : TabManager {
    private val browserActivity = activity as BrowserActivity
    private val supportFragmentManager = activity.supportFragmentManager
    private var openMessageInNewTabJob: Job? = null

    private val keepSingleTab: Boolean
        get() = !browserActivity.tabPager.isUserInputEnabled

    private val coroutineScope = browserActivity.lifecycleScope

    var selectedTabId: String? = null
        private set

    override val tabPagerAdapter by lazy {
        TabPagerAdapter(
            fragmentManager = supportFragmentManager,
            lifecycleOwner = browserActivity,
            activityIntent = browserActivity.intent,
            getSelectedTabId = { selectedTabId },
            getTabById = ::getTabById,
            requestNewTab = ::requestNewTab,
            onTabSelected = { tabId -> openExistingTab(tabId) },
        )
    }

    override val currentTab: BrowserTabFragment?
        get() = tabPagerAdapter.currentFragment

    override fun onSelectedTabChanged(tabId: String) {
        Timber.d("### TabManager.onSelectedTabChanged: $tabId")
        selectedTabId = tabId

        if (keepSingleTab) {
            tabPagerAdapter.onTabsUpdated(listOf(tabId))
        }
    }

    override fun onTabsUpdated(updatedTabIds: List<String>) {
        Timber.d("### TabManager.onTabsUpdated: $updatedTabIds")
        if (keepSingleTab) {
            updatedTabIds.firstOrNull { it == selectedTabId }?.let {
                tabPagerAdapter.onTabsUpdated(listOf(it))
            }
        } else {
            tabPagerAdapter.onTabsUpdated(updatedTabIds)
        }

        if (updatedTabIds.isEmpty()) {
            coroutineScope.launch(dispatchers.io()) {
                Timber.i("Tabs list is null or empty; adding default tab")
                tabRepository.addDefaultTab()
            }
        }
    }

    override fun openMessageInNewTab(message: Message, sourceTabId: String?) {
        openMessageInNewTabJob = coroutineScope.launch {
            tabPagerAdapter.setMessageForNewFragment(message)
            openNewTab(sourceTabId)
        }
    }

    override fun openExistingTab(tabId: String) {
        coroutineScope.launch(dispatchers.io()) {
            if (tabId != tabRepository.getSelectedTab()?.tabId) {
                tabRepository.select(tabId)
            }
        }
    }

    override fun launchNewTab() {
        coroutineScope.launch { openNewTab() }
    }

    override fun openInNewTab(
        query: String,
        sourceTabId: String?,
        skipHome: Boolean,
    ) {
        coroutineScope.launch {
            val url = if (skipUrlConversionOnNewTabFeature.self().isEnabled()) {
                query
            } else {
                queryUrlConverter.convertQueryToUrl(query)
            }

            if (sourceTabId != null) {
                tabRepository.addFromSourceTab(
                    url = url,
                    skipHome = skipHome,
                    sourceTabId = sourceTabId,
                )
            } else {
                tabRepository.add(
                    url = url,
                    skipHome = skipHome,
                )
            }
        }
    }

    override fun clearTabsInMemory() {
        tabPagerAdapter.clearFragments()
    }

    override fun onCleanup() {
        openMessageInNewTabJob?.cancel()
    }

    private suspend fun openNewTab(sourceTabId: String? = null): String {
        return if (sourceTabId != null) {
            tabRepository.addFromSourceTab(sourceTabId = sourceTabId)
        } else {
            tabRepository.add()
        }
    }

    private fun requestNewTab(): TabEntity = runBlocking(dispatchers.io()) {
        val tabId = openNewTab()
        return@runBlocking tabRepository.flowTabs.transformWhile { result ->
            result.firstOrNull { it.tabId == tabId }?.let { entity ->
                emit(entity)
                return@transformWhile true
            }
            return@transformWhile false
        }.first()
    }

    private fun getTabById(tabId: String): TabEntity? = runBlocking {
        tabRepository.getTab(tabId)
    }
}
