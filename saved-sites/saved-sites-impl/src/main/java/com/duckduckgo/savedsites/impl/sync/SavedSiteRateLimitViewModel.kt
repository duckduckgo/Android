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

package com.duckduckgo.savedsites.impl.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.savedsites.impl.sync.DisplayModeViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedSiteRateLimitViewModel(
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val warningVisible: Boolean = false,
    )

    sealed class Command {
        data object NavigateToBookmarks : Command()
    }

    private val command = Channel<Command>(1, DROP_OLDEST)

    fun viewState(): Flow<ViewState> = savedSitesSyncStore.isSyncPausedFlow()
        .map { syncPaused ->
            ViewState(
                warningVisible = syncPaused,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onWarningActionClicked() {
        viewModelScope.launch {
            command.send(Command.NavigateToBookmarks)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val savedSitesSyncStore: SavedSitesSyncStore,
        private val dispatcherProvider: DispatcherProvider,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(SavedSiteRateLimitViewModel::class.java) -> SavedSiteRateLimitViewModel(
                        savedSitesSyncStore,
                        dispatcherProvider,
                    )
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
