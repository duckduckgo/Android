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

package com.duckduckgo.sync.impl.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.AskSyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.RecoverSyncData
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.SyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.SyncAnotherDeviceScreen
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncSetupFlowViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())

    fun viewState(viewMode: ViewMode): Flow<ViewState> = viewState.onStart {
        viewState.emit(ViewState(viewMode = viewMode))
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = SyncAnotherDeviceScreen,
    )

    sealed class ViewMode {
        object SyncAnotherDeviceScreen : ViewMode()
        object InitialSetupScreen : ViewMode()
    }

    sealed class Command {
        object AskSyncAnotherDevice : Command()
        object RecoverSyncData : Command()
        object SyncAnotherDevice : Command()
        object FinishSetupFlow : Command()
        object AbortFlow : Command()
    }

    fun onTurnOnSyncClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(AskSyncAnotherDevice)
        }
    }

    fun onRecoverYourSyncDataClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(RecoverSyncData)
        }
    }

    fun onSyncAnotherDeviceClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(SyncAnotherDevice)
        }
    }

    fun onNotNowClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(FinishSetupFlow)
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(AbortFlow)
        }
    }
}
