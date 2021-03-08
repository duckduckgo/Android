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

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class TabSwitcherViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideTabSwitcherViewModelFactory(
        tabRepository: TabRepository,
        webViewSessionStorage: WebViewSessionStorage
    ): ViewModelFactoryPlugin {
        return TabSwitcherViewModelFactory(tabRepository, webViewSessionStorage)
    }
}

private class TabSwitcherViewModelFactory(
    private val tabRepository: TabRepository,
    private val webViewSessionStorage: WebViewSessionStorage
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(TabSwitcherViewModel::class.java) -> TabSwitcherViewModel(tabRepository, webViewSessionStorage) as T
                else -> null
            }
        }
    }

}

class TabSwitcherViewModel(private val tabRepository: TabRepository, private val webViewSessionStorage: WebViewSessionStorage) : ViewModel() {

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    var deletableTabs: LiveData<List<TabEntity>> = tabRepository.flowDeletableTabs.asLiveData(
        context = viewModelScope.coroutineContext
    )
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data class DisplayMessage(@StringRes val messageId: Int) : Command()
        object Close : Command()
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

    fun onClearComplete() {
        command.value = Command.DisplayMessage(R.string.fireDataCleared)
        command.value = Command.Close
    }
}
