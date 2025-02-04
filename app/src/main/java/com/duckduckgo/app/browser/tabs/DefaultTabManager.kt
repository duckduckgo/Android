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

import com.duckduckgo.app.browser.SkipUrlConversionOnNewTabFeature
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import timber.log.Timber

interface TabManager {
    companion object {
        const val MAX_ACTIVE_TABS = 20
    }

    fun registerCallbacks(onTabsUpdated: (List<String>) -> Unit)
    fun getSelectedTabId(): String?
    fun onSelectedTabChanged(tabId: String)

    suspend fun onTabsChanged(updatedTabIds: List<String>)
    suspend fun switchToTab(tabId: String)
    suspend fun requestAndWaitForNewTab(): TabEntity
    suspend fun openNewTab(query: String? = null, sourceTabId: String? = null, skipHome: Boolean = false): String
    suspend fun getTabById(tabId: String): TabEntity?
}

@ContributesBinding(ActivityScope::class)
class DefaultTabManager @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val skipUrlConversionOnNewTabFeature: SkipUrlConversionOnNewTabFeature,
) : TabManager {
    private lateinit var onTabsUpdated: (List<String>) -> Unit
    private var selectedTabId: String? = null

    override fun registerCallbacks(onTabsUpdated: (List<String>) -> Unit) {
        this.onTabsUpdated = onTabsUpdated
    }

    override fun getSelectedTabId(): String? = selectedTabId

    override fun onSelectedTabChanged(tabId: String) {
        selectedTabId = tabId
    }

    override suspend fun onTabsChanged(updatedTabIds: List<String>) {
        onTabsUpdated(updatedTabIds)

        if (updatedTabIds.isEmpty()) {
            withContext(dispatchers.io()) {
                Timber.i("Tabs list is null or empty; adding default tab")
                tabRepository.addDefaultTab()
            }
        }
    }

    override suspend fun requestAndWaitForNewTab(): TabEntity = withContext(dispatchers.io()) {
        val tabId = openNewTab()
        return@withContext tabRepository.flowTabs.transformWhile { result ->
            result.firstOrNull { it.tabId == tabId }?.let { entity ->
                emit(entity)
                return@transformWhile true
            }
            return@transformWhile false
        }.first()
    }

    override suspend fun switchToTab(tabId: String) = withContext(dispatchers.io()) {
        if (tabId != tabRepository.getSelectedTab()?.tabId) {
            tabRepository.select(tabId)
        }
    }

    override suspend fun openNewTab(
        query: String?,
        sourceTabId: String?,
        skipHome: Boolean,
    ): String = withContext(dispatchers.io()) {
        val url = query?.let {
            if (skipUrlConversionOnNewTabFeature.self().isEnabled()) {
                query
            } else {
                queryUrlConverter.convertQueryToUrl(query)
            }
        }

        return@withContext if (sourceTabId != null) {
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

    override suspend fun getTabById(tabId: String): TabEntity? = withContext(dispatchers.io()) {
        return@withContext tabRepository.getTab(tabId)
    }
}
