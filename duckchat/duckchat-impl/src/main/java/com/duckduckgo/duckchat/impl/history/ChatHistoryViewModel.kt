/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Loaded
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ChatHistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
) : ViewModel() {

    private val searchState = MutableStateFlow(SearchState())

    val uiState: StateFlow<ChatHistoryUiState> = combine(
        chatHistoryRepository.observeChats(),
        searchState,
        ::reduce,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ChatHistoryUiState.Loading,
    )

    fun onSearchActivated() {
        searchState.update { it.copy(active = true) }
    }

    fun onSearchQueryChanged(query: String) {
        searchState.update { it.copy(query = query) }
    }

    fun onSearchClosed() {
        searchState.value = SearchState()
    }

    private fun reduce(items: List<ChatHistoryItem>, search: SearchState): ChatHistoryUiState {
        logcat { "ChatHistory: reduce ${items.size} item(s), searchActive=${search.active}" }
        if (items.isEmpty()) return ChatHistoryUiState.Empty
        val (pinned, recent) = items.partition { it.pinned }
        return Loaded(
            pinned = pinned.sortedByDate().filterBy(search),
            recent = recent.sortedByDate().filterBy(search),
            searchQuery = search.query,
            searchActive = search.active,
            mode = Mode.Default,
        )
    }

    private fun List<ChatHistoryItem>.filterBy(search: SearchState): List<ChatHistoryItem> =
        if (!search.active || search.query.isEmpty()) {
            this
        } else {
            filter { it.displayTitle.contains(search.query, ignoreCase = true) }
        }

    private fun List<ChatHistoryItem>.sortedByDate(): List<ChatHistoryItem> =
        sortedByDescending { it.lastEditMillis }

    private data class SearchState(
        val active: Boolean = false,
        val query: String = "",
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
