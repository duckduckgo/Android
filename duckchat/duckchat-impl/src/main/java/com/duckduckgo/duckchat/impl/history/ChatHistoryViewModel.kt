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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Loaded
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Mode
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.PendingConfirmation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ChatHistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val searchState = MutableStateFlow(SearchState())
    private val confirmationState = MutableStateFlow<PendingConfirmation?>(null)

    /** Cached snapshot so non-suspend action methods can read Recent without re-subscribing. */
    private var latestItems: List<ChatHistoryItem> = emptyList()

    val uiState: StateFlow<ChatHistoryUiState> = combine(
        chatHistoryRepository.observeChats().onEach { latestItems = it },
        searchState,
        confirmationState,
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

    /**
     * 0 Recent → no-op; 1 → delete directly (spares Pinned); ≥2 → confirmation dialog,
     * which clears every Duck.ai chat (Pinned included) via the options-driven path.
     */
    fun onFireAllRequested() {
        val recent = latestItems.filter { !it.pinned }
        when {
            recent.isEmpty() -> {
                logcat { "ChatHistory: Fire-all no-op (Recent empty)" }
            }
            recent.size == 1 -> {
                logcat { "ChatHistory: Fire-all single-chat fast-path" }
                appScope.launch { chatHistoryRepository.deleteChat(recent.single().chatId) }
            }
            else -> {
                logcat { "ChatHistory: Fire-all confirmation requested (count=${recent.size})" }
                confirmationState.value = PendingConfirmation.FireAll(count = recent.size)
            }
        }
    }

    /** Clears the confirmation state. The dialog performs the actual deletion via the options-driven path. */
    fun onFireAllConfirmed() {
        logcat { "ChatHistory: Fire-all confirmed (options-driven path handles deletion)" }
        confirmationState.value = null
    }

    fun onConfirmationCancelled() {
        logcat { "ChatHistory: confirmation cancelled" }
        confirmationState.value = null
    }

    private fun reduce(
        items: List<ChatHistoryItem>,
        search: SearchState,
        confirmation: PendingConfirmation?,
    ): ChatHistoryUiState {
        logcat { "ChatHistory: reduce ${items.size} item(s), searchActive=${search.active}, confirmation=$confirmation" }
        if (items.isEmpty()) return ChatHistoryUiState.Empty
        val (pinned, recent) = items.partition { it.pinned }
        return Loaded(
            pinned = pinned.sortedByDate().filterBy(search),
            recent = recent.sortedByDate().filterBy(search),
            searchQuery = search.query,
            searchActive = search.active,
            mode = Mode.Default,
            confirmation = confirmation,
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
