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

package com.duckduckgo.app.tabs.rules.visitedsites

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.autocomplete.api.formatIfUrl
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.TabRuleEntity
import com.duckduckgo.app.tabs.TabRulesDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.rules.Success
import com.duckduckgo.app.tabs.rules.TabRulesCommand
import com.duckduckgo.app.tabs.rules.visitedsites.VisitedSite.ExistingTab
import com.duckduckgo.app.tabs.rules.visitedsites.VisitedSite.HistoricalSite
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.impl.HistoryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@ContributesViewModel(ActivityScope::class)
class TabRulesVisitedSitesViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val faviconManager: FaviconManager,
    private val tabRulesDao: TabRulesDao,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TabRulesVisitedSitesViewState())
    val state = _state.asStateFlow()

    private val _commands = Channel<TabRulesCommand>()
    val commands = _commands.receiveAsFlow()

    init {
        observeVisitedSites()
    }

    fun onAddSiteRuleStateChanged(
        isChecked: Boolean,
        site: VisitedSite
    ) {
        if (isChecked) {
            onAddSiteRuleClicked(site)
        } else {
            onRemoveSiteRuleClicked(site)
        }
    }

    private fun onAddSiteRuleClicked(visitedSite: VisitedSite) {
        viewModelScope.launch {
            visitedSite.faviconBitmap?.let { faviconManager.persistFavicon(it, visitedSite.url) }

            tabRulesDao.addTabRule(
                TabRuleEntity(
                    url = visitedSite.url,
                    title = visitedSite.title,
                    isEnabled = true,
                    createdAt = LocalDateTime.now(),
                ),
            )
        }
    }

    private fun onRemoveSiteRuleClicked(visitedSite: VisitedSite) {
        viewModelScope.launch {
            tabRulesDao.deleteTabRule(visitedSite.url)
        }
    }

    private fun observeVisitedSites() {
        combine(
            existingTabFlow(),
            historicalSitesFlow(),
        ) { tabs, historicalSites ->
            val visitedSites = (tabs + historicalSites).sortedByDescending { it.count }

            _state.update { it.copy(visitedSites = Success(visitedSites)) }
        }
            .launchIn(viewModelScope)
    }

    private fun existingTabFlow(): Flow<List<VisitedSite>> = combine(
        tabRepository.flowTabs.flowOn(dispatcherProvider.io()),
        tabRulesDao.tabRules().flowOn(dispatcherProvider.io()),
    ) { tabs, tabRules ->
        mapToVisitedSites(tabs, tabRules)
    }
        .flowOn(dispatcherProvider.computation())

    private fun historicalSitesFlow() =
        combine(
            historyRepository.getHistory().flowOn(dispatcherProvider.io()),
            tabRulesDao.tabRules().flowOn(dispatcherProvider.io()),
        ) { historyEntries, tabRules ->
            mapToHistoricalSites(historyEntries, tabRules)
        }

    private suspend fun mapToHistoricalSites(
        historyEntries: List<HistoryEntry>,
        tabRules: List<TabRuleEntity>
    ): List<VisitedSite> =
        historyEntries.map { entry ->
            val faviconBitmap = faviconManager.tryFetchFaviconForUrl(entry.url.toString())

            HistoricalSite(
                url = entry.url.toString(),
                faviconBitmap = faviconBitmap,
                title = entry.title,
                isEnabled = tabRules.any { it.url == entry.url.toString() },
                visitCount = entry.visits.count(),
            )
        }

    private suspend fun mapToVisitedSites(
        tabs: List<TabEntity>,
        tabRules: List<TabRuleEntity>
    ): List<VisitedSite> =
        tabs
            .filterNot { it.url.isNullOrBlank() || it.title.isNullOrBlank() }
            .groupBy { it.url!! }
            .map { tabMap ->
                // TODO probably want a better way of doing this at the View level by providing a url and letting it handle it itself
                val faviconBitmap = faviconManager.loadFromDisk(tabMap.value.first().tabId, tabMap.key)

                ExistingTab(
                    url = tabMap.key,
                    faviconBitmap = faviconBitmap,
                    title = tabMap.value.first().title ?: "",
                    isEnabled = tabRules.any { it.url == tabMap.key },
                    tabCount = tabMap.value.size,
                )
            }
}

sealed class VisitedSite(
    open val url: String,
    open val faviconBitmap: Bitmap?,
    open val title: String,
    open val isEnabled: Boolean = false,
    open val count: Int = 0,
    open val displayUrl: String = url.formatIfUrl(),
) {
    data class ExistingTab(
        override val url: String,
        override val faviconBitmap: Bitmap?,
        override val title: String,
        override val isEnabled: Boolean = false,
        val tabCount: Int,
    ) : VisitedSite(url, faviconBitmap, title, isEnabled, tabCount) {}

    data class HistoricalSite(
        override val url: String,
        override val faviconBitmap: Bitmap?,
        override val title: String,
        override val isEnabled: Boolean = false,
        val visitCount: Int,
    ) : VisitedSite(url, faviconBitmap, title, isEnabled, visitCount)
}
