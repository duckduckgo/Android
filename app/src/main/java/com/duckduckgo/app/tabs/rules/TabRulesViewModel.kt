/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.rules

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.TabRuleEntity
import com.duckduckgo.app.tabs.TabRulesDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class TabRulesViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val tabRulesDao: TabRulesDao,
    private val faviconManager: FaviconManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TabRulesViewState())
    val state = _state.asStateFlow()

    private val _commands = Channel<TabRulesCommand>(1, DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    init {
        observeTabRules()
    }

    fun onTabRuleEnabledChanged(tabRuleId: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            tabRulesDao.updateTabRuleEnabled(tabRuleId, isEnabled)
        }
    }

    fun onTabRuleDeleteClicked(tabRuleId: Long) {
        viewModelScope.launch {
            tabRulesDao.deleteTabRule(tabRuleId)
        }
    }

    private fun observeTabRules() {
        tabRulesDao.tabRules().onEach { tabRuleEntities ->
            _state.update { it.copy(tabRules = tabRuleEntities.mapToTabRules()) }
        }.launchIn(viewModelScope)
    }

    private suspend fun List<TabRuleEntity>.mapToTabRules(): List<TabRule> = map { it.toTabRule() }

    private suspend fun TabRuleEntity.toTabRule(): TabRule {
        val faviconBitmap = faviconManager.loadFromDisk(null, url)

        return TabRule(
            id = id,
            url = url,
            title = title,
            faviconBitmap = faviconBitmap,
            isEnabled = isEnabled,
            createdAt = createdAt,
        )
    }
}
