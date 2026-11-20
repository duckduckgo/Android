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
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class SyncAuditLogRow(
    val item: SyncAuditLogItem,
    val time: RelativeTime,
)

class SyncAuditLogViewModel @AssistedInject constructor(
    @Assisted private val params: SyncAuditLogScreenParams,
    private val loader: SyncAuditLogLoader,
    private val currentTimeProvider: CurrentTimeProvider,
) : ViewModel() {

    data class ViewState(
        val rows: List<SyncAuditLogRow> = emptyList(),
        val status: Status = Status.LOADING,
        val hasMore: Boolean = true,
    ) {
        val content: Content
            get() = when {
                rows.isNotEmpty() -> Content.LIST
                status == Status.LOADING -> Content.LOADING
                status == Status.FAILED -> Content.ERROR
                hasMore -> Content.LIST
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

    enum class Content { LOADING, ERROR, EMPTY, LIST }

    enum class Footer { NONE, LOADING, ERROR }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private var lastLoad: SyncAuditLogLoadResult.Loaded? = null
    private var retryAllowed = false

    init {
        load()
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
        val previous = lastLoad
        if (previous != null && !previous.hasMore) return

        _viewState.update { it.copy(status = Status.LOADING) }
        viewModelScope.launch {
            val result = if (previous == null) {
                loader.loadLatest(params.entriesPerPage)
            } else {
                loader.loadOlder(previous, params.entriesPerPage)
            }
            when (result) {
                SyncAuditLogLoadResult.Failed -> {
                    // A scroll during the failed load must not count: the retry needs a gesture made after the error shows.
                    retryAllowed = false
                    _viewState.update { it.copy(status = Status.FAILED) }
                }
                is SyncAuditLogLoadResult.Loaded -> {
                    lastLoad = result
                    val newRows = result.items.toRows()
                    _viewState.update { it.copy(rows = it.rows + newRows, status = Status.IDLE, hasMore = result.hasMore) }
                }
            }
        }
    }

    // Times are fixed when a page loads: the screen does not refresh while it is open.
    private fun List<SyncAuditLogItem>.toRows(): List<SyncAuditLogRow> {
        val now = Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis())
        return map { SyncAuditLogRow(it, RelativeTime.between(it.occurredAt, now)) }
    }

    @AssistedFactory
    interface Factory {
        fun create(params: SyncAuditLogScreenParams): SyncAuditLogViewModel
    }
}
