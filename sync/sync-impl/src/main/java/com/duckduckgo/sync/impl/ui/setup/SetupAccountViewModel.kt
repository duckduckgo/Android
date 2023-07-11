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
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.DEVICE_CONNECTED
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SETUP
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.RecoverSyncData
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.SyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.DeviceConnected
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.TurnOnSync
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SetupAccountViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    private var initialStateProcessed = false

    fun viewState(screen: Screen): Flow<ViewState> = viewState.onStart {
        if (!initialStateProcessed) {
            val viewMode = when (screen) {
                SETUP -> TurnOnSync
                RECOVERY_CODE -> AskSaveRecoveryCode
                DEVICE_CONNECTED -> DeviceConnected
            }
            viewState.emit(ViewState(viewMode))
            initialStateProcessed = true
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = TurnOnSync,
    )

    sealed class ViewMode {
        object TurnOnSync : ViewMode()
        object AskSyncAnotherDevice : ViewMode()
        object AskSaveRecoveryCode : ViewMode()
        object DeviceConnected : ViewMode()
    }

    sealed class Command {
        object Close : Command()
        object RecoverSyncData : Command()
        object SyncAnotherDevice : Command()
    }

    fun onBackPressed() {
        viewModelScope.launch {
            when (viewState.value.viewMode) {
                AskSyncAnotherDevice -> {
                    viewState.emit(ViewState(viewMode = TurnOnSync))
                }

                TurnOnSync, AskSaveRecoveryCode, DeviceConnected -> {
                    viewModelScope.launch {
                        command.send(Close)
                    }
                }
            }
        }
    }

    fun onAskSyncAnotherDevice() {
        viewModelScope.launch {
            viewState.emit(ViewState(viewMode = AskSyncAnotherDevice))
        }
    }

    fun finishSetupFlow() {
        viewModelScope.launch {
            viewState.emit(ViewState(viewMode = AskSaveRecoveryCode))
        }
    }

    fun onRecoverYourSyncedData() {
        viewModelScope.launch {
            command.send(RecoverSyncData)
        }
    }

    fun onLoginSucess() {
        viewModelScope.launch {
            viewState.emit(ViewState(viewMode = DeviceConnected))
        }
    }

    fun onSyncAnotherDevice() {
        viewModelScope.launch {
            command.send(SyncAnotherDevice)
        }
    }
}
