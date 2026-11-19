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
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SyncAuditLogRow(
    val item: SyncAuditLogItem,
    val time: RelativeTime,
)

@ContributesViewModel(ActivityScope::class)
class SyncAuditLogViewModel @Inject constructor(
    private val loader: SyncAuditLogLoader,
    private val currentTimeProvider: CurrentTimeProvider,
) : ViewModel() {

    sealed interface ViewState {
        data object Loading : ViewState
        data object Empty : ViewState
        data object Error : ViewState
        data class Loaded(val rows: List<SyncAuditLogRow>) : ViewState
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    init {
        viewModelScope.launch { _viewState.value = load() }
    }

    private suspend fun load(): ViewState = when (val result = loader.loadLatest()) {
        SyncAuditLogLoadResult.Failed -> ViewState.Error
        is SyncAuditLogLoadResult.Loaded -> {
            // Times are fixed when the page loads: the screen does not refresh while it is open.
            val now = Instant.ofEpochMilli(currentTimeProvider.currentTimeMillis())
            if (result.items.isEmpty()) {
                ViewState.Empty
            } else {
                ViewState.Loaded(result.items.map { SyncAuditLogRow(it, RelativeTime.between(it.occurredAt, now)) })
            }
        }
    }
}
