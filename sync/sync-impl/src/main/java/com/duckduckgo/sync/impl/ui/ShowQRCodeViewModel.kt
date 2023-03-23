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
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.ShowQRCodeViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowQR
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class ShowQRCodeViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState.onStart {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(dispatchers.io()) {
            showQRCode()
            var polling = true
            while (polling) {
                delay(5000)
                when (syncRepository.pollConnectionKeys()) {
                    is Success -> {
                        command.send(LoginSucess)
                        polling = false
                    }
                    else -> {}
                }
            }
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val qrCode: String? = null,
    )

    sealed class Command {
        object LoginSucess : Command()
        object Error : Command()
    }

    private suspend fun showQRCode() {
        val result = syncRepository.getConnectQR()
        when (result) {
            is Error -> {
                command.send(Command.Error)
            }
            is Success -> {
                viewState.emit(
                    viewState.value.copy(
                        qrCode = result.data
                    ),
                )
            }
        }
    }

}
