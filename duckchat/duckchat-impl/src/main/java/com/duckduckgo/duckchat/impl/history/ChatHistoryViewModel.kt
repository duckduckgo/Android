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
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ChatHistoryViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val duckChat: DuckChatInternal,
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

    /**
     * On every Repository emission, intersect any selection with the current item IDs so a
     * concurrent omnibar Delete or single-row overflow Delete doesn't desync the selection.
     */
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

    /** True when the screen is currently in select mode — read synchronously by the Fragment. */
    fun isSelectMode(): Boolean = modeState.value is Mode.Selecting

    /**
     * Row tap. In default mode resumes the chat in Duck.ai; in select mode toggles the row in
     * the current selection. Centralised here so the Fragment doesn't need to read mode state
     * or hold a `DuckChatInternal` reference.
     */
    fun onChatRowClicked(chatId: String) {
        if (modeState.value is Mode.Selecting) {
            onSelectionToggled(chatId)
        } else {
            duckChat.openWithChatId(chatId)
        }
    }

    /**
     * Row long-press — power-user entry to select mode alongside the toolbar overflow Select
     * action. In default mode enters select mode with the row pre-selected. In select mode
     * toggles the row, matching tap behaviour. Returns true to consume the long-press event.
     */
    fun onChatRowLongClicked(chatId: String): Boolean {
        if (modeState.value !is Mode.Selecting) {
            logcat { "ChatHistory: enter select mode via long-press (chatId=$chatId)" }
            modeState.value = Mode.Selecting(setOf(chatId))
        } else {
            onSelectionToggled(chatId)
        }
        return true
    }

    /** Empty-state CTA — opens Duck.ai for a fresh chat. */
    fun onOpenDuckAiClicked() {
        duckChat.openDuckChat()
    }

    /** Toolbar fire icon. Routes to Delete-selected in select mode, Fire-all otherwise. */
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

    /** Enters select mode with an empty selection. Triggered by the toolbar overflow "Select" entry. */
    fun onEnterSelectMode() {
        logcat { "ChatHistory: enter select mode" }
        modeState.value = Mode.Selecting(emptySet())
    }

    /** Toggles row membership inside the current selection. Empty selection stays in select mode. */
    fun onSelectionToggled(chatId: String) {
        val current = modeState.value as? Mode.Selecting ?: return
        val next = if (chatId in current.selectedChatIds) {
            current.selectedChatIds - chatId
        } else {
            current.selectedChatIds + chatId
        }
        modeState.value = Mode.Selecting(next)
    }

    /** Select-all / Unselect-all toggle in the sticky header row. */
    fun onSelectAllToggled() {
        val current = modeState.value as? Mode.Selecting ?: return
        val visibleIds = visibleChatIds()
        val next = if (current.selectedChatIds == visibleIds) emptySet() else visibleIds
        modeState.value = Mode.Selecting(next)
    }

    /** Exits select mode without deleting. Back-arrow / system back. */
    fun onSelectModeCancelled() {
        logcat { "ChatHistory: select mode cancelled" }
        modeState.value = Mode.Default
    }

    /**
     * Toolbar fire icon in select mode. Branches on selection size:
     *  - 0 → no-op (the icon should be disabled).
     *  - 1 → delete the single chat directly (TODO: route through `ClearableData.DuckChats.Selected`
     *    once T059–T061 land; for now uses `chatHistoryRepository.deleteChat(chatId)`).
     *  - ≥ 2 → confirmation dialog with the captured `chatIds` snapshot.
     */
    fun onDeleteSelectedRequested() {
        val current = modeState.value as? Mode.Selecting ?: return
        val ids = current.selectedChatIds
        when {
            ids.isEmpty() -> logcat { "ChatHistory: Delete-selected no-op (empty selection)" }
            ids.size == 1 -> {
                logcat { "ChatHistory: Delete-selected single-chat fast-path" }
                // Exit select mode before launching so the reconciler doesn't race the delete.
                modeState.value = Mode.Default
                appScope.launch { chatHistoryRepository.deleteChat(ids.single()) }
            }
            else -> {
                logcat { "ChatHistory: Delete-selected confirmation requested (count=${ids.size})" }
                confirmationState.value = PendingConfirmation.DeleteSelected(chatIds = ids)
            }
        }
    }

    /**
     * Clears the confirmation state and exits select mode. The dialog drives the actual deletion
     * via its `selectedChatUrls` plumbing (T057 — TODO: wire when the dialog change lands).
     */
    fun onDeleteSelectedConfirmed() {
        logcat { "ChatHistory: Delete-selected confirmed" }
        // TODO(T058/T064): once the dialog dispatches via `ClearableData.DuckChats.Selected`,
        //  remove this fallback. For now (UI-only landing), the ViewModel iterates the captured
        //  snapshot directly so the feature is end-to-end functional on internal builds.
        val ids = (confirmationState.value as? PendingConfirmation.DeleteSelected)?.chatIds.orEmpty()
        // Exit select mode FIRST so subsequent repository emissions don't race the reconciler —
        // otherwise a delete could observe `Mode.Selecting(ids)` and rewrite `mode` to
        // `Selecting(empty)` before this method finishes.
        confirmationState.value = null
        modeState.value = Mode.Default
        if (ids.isNotEmpty()) {
            appScope.launch { ids.forEach { chatHistoryRepository.deleteChat(it) } }
        }
    }

    /** Snapshot of the IDs currently visible (respects the active search filter). */
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
        logcat { "ChatHistory: reduce ${items.size} item(s), searchActive=${search.active}, confirmation=$confirmation, mode=$mode" }
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
