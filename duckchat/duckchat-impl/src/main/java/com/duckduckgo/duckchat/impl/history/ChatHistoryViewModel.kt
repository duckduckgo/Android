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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ChatHistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatHistoryUiState> = chatHistoryRepository.observeChats()
        .map(::reduce)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ChatHistoryUiState.Loading,
        )

    private fun reduce(items: List<ChatHistoryItem>): ChatHistoryUiState {
        logcat { "ChatHistory: reduce ${items.size} item(s)" }
        if (items.isEmpty()) return ChatHistoryUiState.Empty
        val (pinned, recent) = items.partition { it.pinned }
        return Loaded(
            pinned = pinned.sortedByDate(),
            recent = recent.sortedByDate(),
            mode = Mode.Default,
        )
    }

    private fun List<ChatHistoryItem>.sortedByDate(): List<ChatHistoryItem> =
        sortedByDescending { it.lastEditMillis }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
