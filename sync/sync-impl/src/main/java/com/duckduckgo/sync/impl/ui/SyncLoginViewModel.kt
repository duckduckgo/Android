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
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.ReadTextCode
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncLoginViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        object ReadTextCode : Command()
        object LoginSucess : Command()
        object Error : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            syncPixels.fireLoginPixel()
            command.send(LoginSucess)
        }
    }

    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.processCode(qrCode)
            if (result is Error) {
                command.send(Command.Error)
            } else {
                syncPixels.fireLoginPixel()
                command.send(LoginSucess)
            }
        }
    }
}
