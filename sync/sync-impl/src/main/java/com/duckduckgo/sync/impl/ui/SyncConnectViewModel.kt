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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowQRCode
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncConnectViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart {
        val viewMode = if (syncAccountRepository.isSignedIn()) {
            ViewMode.SignedIn
        } else {
            ViewMode.UnAuthenticated
        }
        viewState.emit(ViewState(viewMode))
    }

    data class ViewState(
        val viewMode: ViewMode = ViewMode.UnAuthenticated,
    )

    sealed class ViewMode {
        object SignedIn : ViewMode()
        object UnAuthenticated : ViewMode()
    }

    sealed class Command {
        object ShowQRCode : Command()
        object ReadTextCode : Command()
        object LoginSucess : Command()
        object Error : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
        }
    }

    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            when (syncAccountRepository.processCode(qrCode)) {
                is Error -> command.send(Command.Error)
                is Success -> command.send(LoginSucess)
            }
        }
    }

    fun onShowQRCodeClicked() {
        viewModelScope.launch {
            command.send(ShowQRCode)
        }
    }

    fun onLoginSucess() {
        viewModelScope.launch {
            command.send(LoginSucess)
        }
    }
}
