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

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.sync.api.engine.FeatureSyncError
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class SavedSiteSyncPausedViewModel @Inject constructor(
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val message: Int? = null,
    )

    sealed class Command {
        data object NavigateToBookmarks : Command()
    }

    private val command = Channel<Command>(1, DROP_OLDEST)

    fun viewState(): Flow<ViewState> = savedSitesSyncStore.isSyncPausedFlow()
        .map { syncPaused ->
            val message = when (savedSitesSyncStore.syncPausedReason) {
                FeatureSyncError.INVALID_REQUEST.name -> R.string.saved_site_invalid_warning
                FeatureSyncError.COLLECTION_LIMIT_REACHED.name -> R.string.saved_site_limit_warning
                else -> null
            }
            ViewState(
                message = message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onWarningActionClicked() {
        viewModelScope.launch {
            command.send(Command.NavigateToBookmarks)
        }
    }
}
