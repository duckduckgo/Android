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

package com.duckduckgo.sync.impl.ui.auditlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.LoginAuditLogFeature
import com.duckduckgo.sync.impl.auditlog.NewLoginNotifier
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

data class SyncAuditLogRow(
    val item: SyncAuditLogItem,
    val time: RelativeTime,
    val highlighted: Boolean = false,
)

class SyncAuditLogViewModel @AssistedInject constructor(
    @Assisted private val params: SyncAuditLogScreenParams,
    private val loader: SyncAuditLogLoader,
    private val currentTimeProvider: CurrentTimeProvider,
    private val stateStore: AuditLogCheckStateStore,
    private val newLoginNotifier: NewLoginNotifier,
    private val loginAuditLogFeature: LoginAuditLogFeature,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
) : ViewModel() {

    data class ViewState(
        val rows: List<SyncAuditLogRow> = emptyList(),
        val status: Status = Status.LOADING,
        val hasMore: Boolean = true,
        val onlyThisDevice: Boolean = false,
    ) {
        val content: Content
            get() = when {
                rows.isNotEmpty() -> Content.LIST
                status == Status.LOADING -> Content.LOADING
                status == Status.FAILED -> Content.ERROR
                hasMore -> Content.LIST
                onlyThisDevice -> Content.ONLY_THIS_DEVICE
                else -> Content.EMPTY
            }

        val footer: Footer
            get() = when {
                rows.isEmpty() -> Footer.NONE
                status == Status.LOADING -> Footer.LOADING
                status == Status.FAILED -> Footer.ERROR
                else -> Footer.NONE
            }
    }

    enum class Status { IDLE, LOADING, FAILED }

    enum class Content { LOADING, ERROR, EMPTY, ONLY_THIS_DEVICE, LIST }

    enum class Footer { NONE, LOADING, ERROR }

    sealed interface Command {
        data object Close : Command
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private class Session(
        val monitor: SyncAuditLogUnseenMonitor,
        var lastLoad: SyncAuditLogLoadResult.Loaded,
    )

    private var session: Session? = null
    private var retryAllowed = false

    init {
        if (params.openedFromNotification) {
            viewModelScope.launch {
                if (loginAuditLogFeature.isAuditLogEnabled()) {
                    load()
                } else {
                    _commands.send(Command.Close)
                }
            }
        } else {
            load()
        }
    }

    fun onNearListEnd() {
        val state = _viewState.value
        if (state.status == Status.LOADING) return
        // Without this, every scroll event near the end retries while offline.
        if (state.status == Status.FAILED && !retryAllowed) return
        load()
    }

    fun onUserScrolled() {
        retryAllowed = true
    }

    fun onRetryClicked() {
        if (_viewState.value.status == Status.FAILED) load()
    }

    private fun load() {
        val current = session
        if (current != null && !current.lastLoad.hasMore) return

        _viewState.update { it.copy(status = Status.LOADING) }
        viewModelScope.launch {
            if (current == null) loadLatest() else loadOlder(current)
        }
    }

    private suspend fun loadLatest() {
        val monitor = readUnseenMonitor()
        when (val result = loader.loadLatest(params.entriesPerPage)) {
            SyncAuditLogLoadResult.Failed -> onLoadFailed()
            is SyncAuditLogLoadResult.Loaded -> {
                session = Session(monitor, result)
                markSeen(monitor, newestEntryId = result.pages.entries.firstOrNull()?.id)
                val onlyThisDevice = connectedDevicesRepository.latestConnectedDeviceCount() == 1
                _viewState.update { it.copy(onlyThisDevice = onlyThisDevice) }
                onLoaded(result, monitor.unseenIds(result.pages.entries, result.items))
            }
        }
    }

    private suspend fun loadOlder(session: Session) {
        val previous = session.lastLoad
        when (val result = loader.loadOlder(previous, params.entriesPerPage)) {
            SyncAuditLogLoadResult.Failed -> onLoadFailed()
            is SyncAuditLogLoadResult.Loaded -> {
                session.lastLoad = result
                val newEntries = result.pages.entries.drop(previous.pages.entries.size)
                onLoaded(result, session.monitor.unseenIds(newEntries, result.items))
            }
        }
    }

    private fun onLoadFailed() {
        // A scroll during the failed load must not count: the retry needs a gesture made after the error shows.
        retryAllowed = false
        _viewState.update { it.copy(status = Status.FAILED) }
    }

    private fun onLoaded(
        result: SyncAuditLogLoadResult.Loaded,
        unseenIds: Set<String>,
    ) {
        val newRows = result.toRows(unseenIds)
        _viewState.update { it.copy(rows = it.rows + newRows, status = Status.IDLE, hasMore = result.hasMore) }
    }

    private suspend fun readUnseenMonitor(): SyncAuditLogUnseenMonitor {
        val state = stateStore.read()
        return SyncAuditLogUnseenMonitor(state.pendingLogins.mapTo(HashSet()) { it.entryId }, state.lastSeenEntryId)
    }

    // Only the logins read at open: a check that runs during the load can add one this screen does not show.
    private suspend fun markSeen(
        monitor: SyncAuditLogUnseenMonitor,
        newestEntryId: String?,
    ) {
        stateStore.update { state ->
            val updated = state.copy(
                pendingLogins = state.pendingLogins.filterNot { it.entryId in monitor.pendingLoginIds },
                lastSeenEntryId = newestEntryId ?: state.lastSeenEntryId,
            )
            updated.takeIf { it != state }
        }
        newLoginNotifier.cancel()
    }

    // Times are fixed when a page loads: the screen does not refresh while it is open.
    private fun SyncAuditLogLoadResult.Loaded.toRows(unseenIds: Set<String>): List<SyncAuditLogRow> {
        val now = Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis())
        endHighlightLater(unseenIds)
        return items.map { SyncAuditLogRow(it, RelativeTime.between(it.occurredAt, now), highlighted = it.entryId in unseenIds) }
    }

    private fun endHighlightLater(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            delay(HIGHLIGHT_DURATION)
            _viewState.update { state ->
                state.copy(rows = state.rows.map { if (it.item.entryId in ids) it.copy(highlighted = false) else it })
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(params: SyncAuditLogScreenParams): SyncAuditLogViewModel
    }

    private companion object {
        val HIGHLIGHT_DURATION = 3.seconds
    }
}
