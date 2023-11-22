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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.DEVICE_SYNCED
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_SETUP
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.RecoverData
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.SettingUpSync
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.CreateAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.DeviceSynced
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.IntroRecoveryCode
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
                SYNC_SETUP -> CreateAccount
                DEVICE_SYNCED -> DeviceSynced
                RECOVERY_CODE -> AskSaveRecoveryCode
                SYNC_INTRO -> IntroCreateAccount
                RECOVERY_INTRO -> IntroRecoveryCode
            }
            viewState.emit(ViewState(viewMode))
            initialStateProcessed = true
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = DeviceSynced,
    )

    sealed class ViewMode {
        object CreateAccount : ViewMode()
        object AskSaveRecoveryCode : ViewMode()
        object DeviceSynced : ViewMode()

        object IntroCreateAccount : ViewMode()
        object IntroRecoveryCode : ViewMode()
    }

    sealed class Command {
        object RecoverData : Command()
        object SettingUpSync : Command()
        object Close : Command()
    }

    fun onBackPressed() {
        viewModelScope.launch {
            command.send(Close)
        }
    }

    fun onSetupComplete() {
        viewModelScope.launch {
            command.send(SettingUpSync)
        }
    }

    fun onSetupStart() {
        viewModelScope.launch {
            viewState.emit(ViewState(viewMode = CreateAccount))
        }
    }

    fun onRecoverAccountStart() {
        viewModelScope.launch {
            command.send(RecoverData)
        }
    }
}
