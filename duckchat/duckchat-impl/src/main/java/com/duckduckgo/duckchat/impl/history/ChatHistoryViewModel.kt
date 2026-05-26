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
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Loaded
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.Mode
import com.duckduckgo.duckchat.impl.history.ChatHistoryUiState.PendingConfirmation
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.toModelDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckAiModelManager: DuckAiModelManager,
) : ViewModel() {

    private val controls = MutableStateFlow(UiControls())

    private val navigationChannel = Channel<NavigationEvent>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val navigationEvents: Flow<NavigationEvent> = navigationChannel.receiveAsFlow()

    private val messageChannel = Channel<MessageEvent>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messageEvents: Flow<MessageEvent> = messageChannel.receiveAsFlow()

    /** Cached snapshot so non-suspend action methods can read Recent without re-subscribing. */
    private var latestItems: List<ChatHistoryItem> = emptyList()

    val uiState: StateFlow<ChatHistoryUiState> = combine(
        chatHistoryRepository.observeChats().onEach { latestItems = it },
        controls,
        ::reduce,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ChatHistoryUiState.Loading,
    )

    fun isSelectMode(): Boolean = controls.value.mode is Mode.Selecting

    fun onChatRowClicked(chatId: String) {
        if (controls.value.mode is Mode.Selecting) {
            onSelectionToggled(chatId)
        } else {
            duckChat.openWithChatId(chatId)
        }
    }

    /** Long-press enters select mode with the row pre-selected; returns true to consume the event. */
    fun onChatRowLongClicked(chatId: String): Boolean {
        controls.update { c ->
            val nextMode = when (val mode = c.mode) {
                is Mode.Selecting -> Mode.Selecting(toggle(mode.selectedChatIds, chatId))
                Mode.Default -> Mode.Selecting(setOf(chatId))
            }
            c.copy(mode = nextMode)
        }
        return true
    }

    fun onOpenDuckAiClicked() {
        duckChat.openDuckChat()
    }

    fun onFireIconClicked() {
        if (controls.value.mode is Mode.Selecting) {
            onDeleteSelectedRequested()
        } else {
            onFireAllRequested()
        }
    }

    fun onSearchActivated() {
        controls.update { it.copy(search = it.search.copy(active = true)) }
    }

    fun onSearchQueryChanged(query: String) {
        controls.update { it.copy(search = it.search.copy(query = query)) }
    }

    fun onSearchClosed() {
        controls.update { it.copy(search = SearchState()) }
    }

    /** Fire-all wipes every Duck.ai chat including Pinned — always confirms via dialog before deleting. */
    fun onFireAllRequested() {
        val all = latestItems
        if (all.isEmpty()) return
        controls.update {
            it.copy(confirmation = PendingConfirmation.FireAll(chatIds = all.mapTo(mutableSetOf()) { i -> i.chatId }))
        }
    }

    /** Per-row overflow Delete — fires immediately, no confirmation. */
    fun onDeleteSingleChat(chatId: String) {
        dispatchSelectedClear(setOf(chatId))
    }

    fun onRenameRequested(chatId: String, currentTitle: String) {
        navigationChannel.trySend(NavigationEvent.OpenRename(chatId = chatId, currentTitle = currentTitle))
    }

    fun onTogglePin(chatId: String) {
        val current = latestItems.firstOrNull { it.chatId == chatId } ?: return
        val wasPinned = current.pinned
        appScope.launch { chatHistoryRepository.setPinned(chatId, !wasPinned) }
        messageChannel.trySend(MessageEvent.PinToggled(chatId = chatId, wasPinned = wasPinned))
    }

    fun onUndoTogglePin(chatId: String, restorePinned: Boolean) {
        appScope.launch { chatHistoryRepository.setPinned(chatId, restorePinned) }
    }

    fun onDownloadRequested(chatId: String) {
        // Snapshot-read /duckchat/v1/models cache so the export header carries provider attribution
        // (e.g. "using OpenAI's GPT-5 mini Model"). Null when the model isn't cached — the exporter
        // then falls back to "using the <raw-id> Model", which is still valid output.
        val modelId = latestItems.firstOrNull { it.chatId == chatId }?.model
        val modelDisplay = modelId
            ?.let { id -> duckAiModelManager.modelState.value.models.firstOrNull { it.id == id } }
            ?.toModelDisplay()
        viewModelScope.launch {
            runCatching { chatHistoryRepository.exportChat(chatId, modelDisplay) }
                .onSuccess { file -> navigationChannel.trySend(NavigationEvent.ShowDownloadComplete(file.name)) }
                .onFailure { navigationChannel.trySend(NavigationEvent.ShowExportError) }
        }
    }

    private fun dispatchSelectedClear(chatIds: Set<String>) {
        if (chatIds.isEmpty()) return
        if (!duckAiFeatureState.showClearDuckAIChatHistory.value) return
        val urls = chatIds.mapTo(mutableSetOf()) { duckChat.buildChatUrl(it) }
        appScope.launch {
            dataClearingTrigger.clearData(setOf(ClearableData.DuckChats.Selected(urls)))
        }
    }

    /** The dialog drives the actual deletion via the URL set surfaced by [chatUrlsForDialog]. */
    fun onFireAllConfirmed() {
        controls.update { it.copy(confirmation = null) }
    }

    fun onConfirmationCancelled() {
        controls.update { it.copy(confirmation = null) }
    }

    fun onEnterSelectMode() {
        controls.update { it.copy(mode = Mode.Selecting(emptySet())) }
    }

    fun onSelectionToggled(chatId: String) {
        controls.update { c ->
            val mode = c.mode as? Mode.Selecting ?: return@update c
            c.copy(mode = Mode.Selecting(toggle(mode.selectedChatIds, chatId)))
        }
    }

    fun onSelectAllToggled() {
        controls.update { c ->
            val mode = c.mode as? Mode.Selecting ?: return@update c
            val visibleIds = visibleChatIds(c.search)
            // Filter to live ids — selection can lag deletes and skew the comparison.
            val effectiveSelected = mode.selectedChatIds intersect latestItems.mapTo(mutableSetOf()) { it.chatId }
            val next = if (effectiveSelected == visibleIds) emptySet() else visibleIds
            c.copy(mode = Mode.Selecting(next))
        }
    }

    fun onSelectModeCancelled() {
        controls.update { it.copy(mode = Mode.Default) }
    }

    fun onDeleteSelectedRequested() {
        val current = controls.value.mode as? Mode.Selecting ?: return
        val ids = current.selectedChatIds
        when {
            ids.isEmpty() -> Unit
            ids.size == 1 -> {
                controls.update { it.copy(mode = Mode.Default) }
                dispatchSelectedClear(ids)
            }
            else -> controls.update {
                it.copy(confirmation = PendingConfirmation.DeleteSelected(chatIds = ids))
            }
        }
    }

    /**
     * The dialog drives the actual deletion via the URL set surfaced by [chatUrlsForDialog].
     * Both fields update atomically (one frame, not two) — keeps the test contract simple.
     */
    fun onDeleteSelectedConfirmed() {
        controls.update { it.copy(confirmation = null, mode = Mode.Default) }
    }

    /** Snapshot of the captured chat IDs (resolved to URLs) for the pending confirmation. */
    fun chatUrlsForDialog(): Set<String>? {
        val ids = controls.value.confirmation?.chatIds ?: return null
        if (ids.isEmpty()) return null
        return ids.mapTo(mutableSetOf()) { duckChat.buildChatUrl(it) }
    }

    private fun visibleChatIds(search: SearchState): Set<String> =
        latestItems
            .asSequence()
            .filter { item -> !search.active || search.query.isEmpty() || item.displayTitle.contains(search.query, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.chatId }

    private fun reduce(
        items: List<ChatHistoryItem>,
        controls: UiControls,
    ): ChatHistoryUiState {
        if (items.isEmpty()) return ChatHistoryUiState.Empty
        val (pinned, recent) = items.partition { it.pinned }
        val effectiveMode = when (val mode = controls.mode) {
            is Mode.Selecting -> Mode.Selecting(mode.selectedChatIds intersect items.mapTo(mutableSetOf()) { it.chatId })
            Mode.Default -> Mode.Default
        }
        return Loaded(
            pinned = pinned.sortedByDate().filterBy(controls.search),
            recent = recent.sortedByDate().filterBy(controls.search),
            searchQuery = controls.search.query,
            searchActive = controls.search.active,
            mode = effectiveMode,
            confirmation = controls.confirmation,
        )
    }

    private fun toggle(current: Set<String>, chatId: String): Set<String> =
        if (chatId in current) current - chatId else current + chatId

    private fun List<ChatHistoryItem>.filterBy(search: SearchState): List<ChatHistoryItem> =
        if (!search.active || search.query.isEmpty()) {
            this
        } else {
            filter { it.displayTitle.contains(search.query, ignoreCase = true) }
        }

    private fun List<ChatHistoryItem>.sortedByDate(): List<ChatHistoryItem> =
        sortedByDescending { it.lastEditMillis }

    private data class UiControls(
        val search: SearchState = SearchState(),
        val confirmation: PendingConfirmation? = null,
        val mode: Mode = Mode.Default,
    )

    private data class SearchState(
        val active: Boolean = false,
        val query: String = "",
    )

    sealed interface NavigationEvent {
        data class OpenRename(val chatId: String, val currentTitle: String) : NavigationEvent
        data class ShowDownloadComplete(val fileName: String) : NavigationEvent
        data object ShowExportError : NavigationEvent
    }

    sealed interface MessageEvent {
        data class PinToggled(val chatId: String, val wasPinned: Boolean) : MessageEvent
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
