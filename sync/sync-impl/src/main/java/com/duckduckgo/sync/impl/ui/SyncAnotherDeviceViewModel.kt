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

import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.app.global.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.impl.*
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.Command.LaunchSaveRecoveryCodeScreen
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.ViewMode.SyncAnotherDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SyncAnotherDeviceViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = SyncAnotherDevice,
    )

    sealed class ViewMode {
        object SyncAnotherDevice : ViewMode()
    }

    sealed class Command {
        object LaunchSaveRecoveryCodeScreen : Command()
        object Finish : Command()
    }

    fun onNotNowClicked() {
        viewModelScope.launch {
            command.send(LaunchSaveRecoveryCodeScreen)
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch {
            command.send(Finish)
        }
    }
}
