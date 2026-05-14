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
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingTrigger
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
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
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ChatHistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val duckChat: DuckChatInternal,
    private val dataClearingTrigger: DataClearingTrigger,
) : ViewModel() {

    private val searchState = MutableStateFlow(SearchState())
    private val confirmationState = MutableStateFlow<PendingConfirmation?>(null)
    private val modeState = MutableStateFlow<Mode>(Mode.Default)

    /** Cached snapshot so non-suspend action methods can read Recent without re-subscribing. */
    private var latestItems: List<ChatHistoryItem> = emptyList()

    val uiState: StateFlow<ChatHistoryUiState> = combine(
        chatHistoryRepository.observeChats().onEach { items ->
            latestItems = items
            reconcileSelection(items)
        },
        searchState,
        confirmationState,
        modeState,
        ::reduce,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ChatHistoryUiState.Loading,
    )

    /** Intersect any selection with the current item IDs so concurrent deletes don't desync it. */
    private fun reconcileSelection(items: List<ChatHistoryItem>) {
        val current = modeState.value
        if (current is Mode.Selecting && current.selectedChatIds.isNotEmpty()) {
            val knownIds = items.mapTo(mutableSetOf()) { it.chatId }
            val intersected = current.selectedChatIds.intersect(knownIds)
            if (intersected.size != current.selectedChatIds.size) {
                modeState.value = Mode.Selecting(intersected)
            }
        }
    }

    fun isSelectMode(): Boolean = modeState.value is Mode.Selecting

    fun onChatRowClicked(chatId: String) {
        if (modeState.value is Mode.Selecting) {
            onSelectionToggled(chatId)
        } else {
            duckChat.openWithChatId(chatId)
        }
    }

    /** Long-press enters select mode with the row pre-selected; returns true to consume the event. */
    fun onChatRowLongClicked(chatId: String): Boolean {
        if (modeState.value !is Mode.Selecting) {
            modeState.value = Mode.Selecting(setOf(chatId))
        } else {
            onSelectionToggled(chatId)
        }
        return true
    }

    fun onOpenDuckAiClicked() {
        duckChat.openDuckChat()
    }

    fun onFireIconClicked() {
        if (modeState.value is Mode.Selecting) {
            onDeleteSelectedRequested()
        } else {
            onFireAllRequested()
        }
    }

    fun onSearchActivated() {
        searchState.update { it.copy(active = true) }
    }

    fun onSearchQueryChanged(query: String) {
        searchState.update { it.copy(query = query) }
    }

    fun onSearchClosed() {
        searchState.value = SearchState()
    }

    /** N=1 spares Pinned; N≥2 routes through the dialog, which wipes every Duck.ai chat. */
    fun onFireAllRequested() {
        val recent = latestItems.filter { !it.pinned }
        when {
            recent.isEmpty() -> Unit
            recent.size == 1 -> dispatchSelectedClear(setOf(recent.single().chatId))
            else -> confirmationState.value = PendingConfirmation.FireAll(count = recent.size)
        }
    }

    private fun dispatchSelectedClear(chatIds: Set<String>) {
        if (chatIds.isEmpty()) return
        val urls = chatIds.mapTo(mutableSetOf()) { duckChat.buildChatUrl(it) }
        appScope.launch {
            dataClearingTrigger.clearData(setOf(ClearableData.DuckChats.Selected(urls)))
        }
    }

    /** The dialog drives the actual deletion via its options-driven path. */
    fun onFireAllConfirmed() {
        confirmationState.value = null
    }

    fun onConfirmationCancelled() {
        confirmationState.value = null
    }

    fun onEnterSelectMode() {
        modeState.value = Mode.Selecting(emptySet())
    }

    fun onSelectionToggled(chatId: String) {
        val current = modeState.value as? Mode.Selecting ?: return
        val next = if (chatId in current.selectedChatIds) {
            current.selectedChatIds - chatId
        } else {
            current.selectedChatIds + chatId
        }
        modeState.value = Mode.Selecting(next)
    }

    fun onSelectAllToggled() {
        val current = modeState.value as? Mode.Selecting ?: return
        val visibleIds = visibleChatIds()
        val next = if (current.selectedChatIds == visibleIds) emptySet() else visibleIds
        modeState.value = Mode.Selecting(next)
    }

    fun onSelectModeCancelled() {
        modeState.value = Mode.Default
    }

    fun onDeleteSelectedRequested() {
        val current = modeState.value as? Mode.Selecting ?: return
        val ids = current.selectedChatIds
        when {
            ids.isEmpty() -> Unit
            ids.size == 1 -> {
                modeState.value = Mode.Default
                dispatchSelectedClear(ids)
            }
            else -> confirmationState.value = PendingConfirmation.DeleteSelected(chatIds = ids)
        }
    }

    /** The dialog drives the actual deletion via the URL set surfaced by [chatUrlsForDialog]. */
    fun onDeleteSelectedConfirmed() {
        confirmationState.value = null
        modeState.value = Mode.Default
    }

    /** Snapshot of the captured chat IDs (resolved to URLs) for the pending DeleteSelected confirmation. */
    fun chatUrlsForDialog(): Set<String>? {
        val ids = (confirmationState.value as? PendingConfirmation.DeleteSelected)?.chatIds ?: return null
        if (ids.isEmpty()) return null
        return ids.mapTo(mutableSetOf()) { duckChat.buildChatUrl(it) }
    }

    private fun visibleChatIds(): Set<String> {
        val search = searchState.value
        return latestItems
            .asSequence()
            .filter { item -> !search.active || search.query.isEmpty() || item.displayTitle.contains(search.query, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.chatId }
    }

    private fun reduce(
        items: List<ChatHistoryItem>,
        search: SearchState,
        confirmation: PendingConfirmation?,
        mode: Mode,
    ): ChatHistoryUiState {
        if (items.isEmpty()) return ChatHistoryUiState.Empty
        val (pinned, recent) = items.partition { it.pinned }
        return Loaded(
            pinned = pinned.sortedByDate().filterBy(search),
            recent = recent.sortedByDate().filterBy(search),
            searchQuery = search.query,
            searchActive = search.active,
            mode = mode,
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
