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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SetupAccountViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.SetupAccountViewModel.ViewMode.SyncAnotherDevice
import com.duckduckgo.sync.impl.ui.SetupAccountViewModel.ViewMode.TurnOnSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SetupAccountViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = TurnOnSync
    )

    sealed class ViewMode {
        object TurnOnSync: ViewMode()
        object SyncAnotherDevice: ViewMode()
    }

    sealed class Command {
        object Finish: Command()
    }
    fun onBackPressed() {
        viewModelScope.launch {
            when (viewState.value.viewMode) {
                SyncAnotherDevice -> {
                    viewState.emit(
                        viewState.value.copy(
                            viewMode = TurnOnSync
                        ),
                    )
                }
                TurnOnSync -> {
                    viewModelScope.launch {
                        command.send(Finish)
                    }
                }
            }
        }
    }

    fun onTurnOnSync() {
        viewModelScope.launch {
            viewState.emit(
                viewState.value.copy(
                    viewMode = SyncAnotherDevice
                ),
            )
        }
    }
}
