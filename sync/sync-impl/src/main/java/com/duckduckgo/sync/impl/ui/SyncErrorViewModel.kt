/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.error.SyncUnavailableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // does not subscribe to app lifecycle
@ContributesViewModel(ViewScope::class)
class SyncErrorViewModel @Inject constructor(
    private val syncErrorRepository: SyncUnavailableRepository,
    private val syncStateMonitor: SyncStateMonitor,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val message: Int? = null,
    )

    private val mutableViewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = mutableViewState

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        syncStateMonitor.syncState().map { state ->
            mutableViewState.emit(mutableViewState.value.copy(message = getMessage(state)))
        }.flowOn(dispatcherProvider.io()).launchIn(viewModelScope)
    }

    private fun getMessage(state: SyncState): Int? {
        if (state == SyncState.OFF) return null

        if (syncErrorRepository.isSyncUnavailable()) {
            return R.string.sync_error_warning
        }
        return null
    }
}
