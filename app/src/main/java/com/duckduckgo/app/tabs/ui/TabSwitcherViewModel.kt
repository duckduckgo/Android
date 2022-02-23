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

package com.duckduckgo.app.tabs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class TabSwitcherViewModel(
    private val tabRepository: TabRepository,
    private val webViewSessionStorage: WebViewSessionStorage
) : ViewModel() {

    data class ViewState(
        val tabs: List<TabEntity> = emptyList(),
        val deletableTabs: List<TabEntity> = emptyList()
    )

    private val viewState = MutableStateFlow(ViewState())
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        object Close : Command()
    }

    fun start() {
        viewModelScope.launch {
            tabRepository.flowTabs.collect {
                viewState.emit(currentViewState().copy(tabs = it))
            }
            tabRepository.flowDeletableTabs.collect {
                viewState.emit(currentViewState().copy(deletableTabs = it))
            }
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    suspend fun onNewTabRequested() {
        tabRepository.add()
        command.value = Command.Close
    }

    suspend fun onTabSelected(tab: TabEntity) {
        tabRepository.select(tab.tabId)
        command.value = Command.Close
    }

    suspend fun onTabDeleted(tab: TabEntity) {
        tabRepository.delete(tab)
        webViewSessionStorage.deleteSession(tab.tabId)
    }

    suspend fun onMarkTabAsDeletable(tab: TabEntity) {
        tabRepository.markDeletable(tab)
    }

    suspend fun undoDeletableTab(tab: TabEntity) {
        tabRepository.undoDeletable(tab)
    }

    suspend fun purgeDeletableTabs() {
        tabRepository.purgeDeletableTabs()
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}

@ContributesMultibinding(AppScope::class)
class TabSwitcherViewModelFactory @Inject constructor(
    private val tabRepository: Provider<TabRepository>,
    private val webViewSessionStorage: Provider<WebViewSessionStorage>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(TabSwitcherViewModel::class.java) -> TabSwitcherViewModel(tabRepository.get(), webViewSessionStorage.get()) as T
                else -> null
            }
        }
    }
}
