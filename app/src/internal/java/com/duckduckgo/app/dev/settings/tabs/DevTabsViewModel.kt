/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val randomUrls = listOf(
    "https://duckduckgo.com",
    "https://blog.duckduckgo.com",
    "https://duck.com",
    "https://privacy.com",
    "https://spreadprivacy.com",
    "https://wikipedia.org",
    "https://privacyguides.org",
    "https://tosdr.org",
    "https://signal.org",
    "https://eff.org",
    "https://fsf.org",
    "https://opensource.org",
    "https://archive.org",
    "https://torproject.org",
    "https://linux.org",
    "https://gnu.org",
    "https://apache.org",
    "https://debian.org",
    "https://ubuntu.com",
    "https://openbsd.org",
)

@ContributesViewModel(ActivityScope::class)
class DevTabsViewModel @Inject constructor(
    private val dispatcher: DispatcherProvider,
    private val tabDataRepository: TabDataRepository,
    private val savedSitesRepository: SavedSitesRepository,
) : ViewModel() {

    data class ViewState(
        val tabCount: Int = 0,
        val bookmarkCount: Int = 0,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    init {
        tabDataRepository.flowTabs
            .onEach { tabs ->
                _viewState.update { it.copy(tabCount = tabs.count()) }
            }
            .flowOn(dispatcher.io())
            .launchIn(viewModelScope)

        savedSitesRepository.getBookmarks()
            .onEach { bookmarks ->
                _viewState.update { it.copy(bookmarkCount = bookmarks.count()) }
            }
            .flowOn(dispatcher.io())
            .launchIn(viewModelScope)
    }

    fun addTabs(count: Int) {
        viewModelScope.launch {
            repeat(count) {
                val randomIndex = randomUrls.indices.random()
                tabDataRepository.add(
                    url = randomUrls[randomIndex],
                )
            }
        }
    }

    fun clearTabs() {
        viewModelScope.launch(dispatcher.io()) {
            tabDataRepository.deleteAll()
        }
    }

    fun addBookmarks(count: Int) {
        viewModelScope.launch(dispatcher.io()) {
            repeat(count) {
                val randomIndex = randomUrls.indices.random()
                savedSitesRepository.insertBookmark(
                    title = "",
                    url = randomUrls[randomIndex]
                )
            }
        }
    }

    fun clearBookmarks() {
        viewModelScope.launch(dispatcher.io()) {
            savedSitesRepository.deleteAll()
        }
    }
}
