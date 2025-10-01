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
import com.duckduckgo.app.dev.settings.tabs.DevTabsViewModel.Command.NoMoreCandidatesForBookmarks
import com.duckduckgo.app.dev.settings.tabs.DevTabsViewModel.Command.NoMoreCandidatesForFavorites
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    "https://letsencrypt.org",
    "https://wikimedia.org",
    "https://creativecommons.org",
    "https://openstreetmap.org",
    "https://kde.org",
    "https://gnome.org",
    "https://blender.org",
    "https://python.org",
    "https://matrix.org",
    "https://github.com/duckduckgo/Android",
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
        val favoritesCount: Int = 0,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

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

        savedSitesRepository.getFavorites()
            .onEach { favorites ->
                _viewState.update { it.copy(favoritesCount = favorites.count()) }
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
            val candidates = randomUrls.filter {
                savedSitesRepository.getBookmark(url = it) == null
            }.toMutableSet()
            repeat(count) {
                if (candidates.size > 0) {
                    val candidate = candidates.random()
                    savedSitesRepository.insertBookmark(
                        title = "",
                        url = candidate,
                    )
                    candidates.remove(candidate)
                } else {
                    _commands.trySend(NoMoreCandidatesForBookmarks)
                    return@repeat
                }
            }
        }
    }

    fun clearBookmarks() {
        viewModelScope.launch(dispatcher.io()) {
            savedSitesRepository.deleteAll()
        }
    }

    fun addFavorites(count: Int) {
        viewModelScope.launch(dispatcher.io()) {
            val candidates = randomUrls.filter {
                savedSitesRepository.getFavorite(url = it) == null
            }.toMutableSet()
            repeat(count) {
                if (candidates.size > 0) {
                    val candidate = candidates.random()
                    savedSitesRepository.insertFavorite(
                        title = "",
                        url = candidate,
                    )
                    candidates.remove(candidate)
                } else {
                    _commands.trySend(NoMoreCandidatesForFavorites)
                    return@repeat
                }
            }
        }
    }

    fun clearFavorites() {
        viewModelScope.launch(dispatcher.io()) {
            savedSitesRepository.getFavorites().firstOrNull()?.forEach {
                savedSitesRepository.delete(it)
            }
        }
    }

    sealed class Command {
        data object NoMoreCandidatesForBookmarks : Command()
        data object NoMoreCandidatesForFavorites : Command()
    }
}
