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

sealed interface ChatHistoryUiState {
    data object Loading : ChatHistoryUiState
    data object Empty : ChatHistoryUiState
    data class Loaded(
        val pinned: List<ChatHistoryItem>,
        val recent: List<ChatHistoryItem>,
        val searchQuery: String = "",
        val searchActive: Boolean = false,
        val mode: Mode = Mode.Default,
        val confirmation: PendingConfirmation? = null,
    ) : ChatHistoryUiState

    sealed interface Mode {
        data object Default : Mode
        data class Selecting(val selectedChatIds: Set<String>) : Mode
    }

    sealed interface PendingConfirmation {
        /** Fire-all confirmation; set when count ≥ 2 (count == 1 deletes directly). */
        data class FireAll(val count: Int) : PendingConfirmation

        /**
         * Delete-selected confirmation — placeholder for future selection-mode work. No caller
         * sets it yet; the current dialog path clears every Duck.ai chat and cannot scope to a subset.
         */
        data class DeleteSelected(val count: Int) : PendingConfirmation
    }
}
