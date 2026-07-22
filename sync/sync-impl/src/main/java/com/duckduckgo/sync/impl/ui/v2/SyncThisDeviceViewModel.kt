/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncThisDeviceViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
    private val syncSetupWideEvent: SyncSetupWideEvent,
) : ViewModel() {
    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun syncThisDevice(source: String?) {
        _viewState.update { it.copy(isSyncing = true) }

        viewModelScope.launch(dispatchers.io()) {
            syncSetupWideEvent.onSyncEnabled()

            suspend fun getDeviceAndFinish() {
                val device = syncAccountRepository.getThisConnectedDevice()
                if (device != null) {
                    _commands.send(Command.FinishSyncing(device))
                } else {
                    syncSetupWideEvent.onAccountCreationFailed()
                    _commands.send(
                        Command.ShowError(R.string.sync_create_account_generic_error),
                    )
                }
            }

            if (syncAccountRepository.isSignedIn()) {
                getDeviceAndFinish()
            } else {
                when (val result = syncAccountRepository.createAccount()) {
                    is Result.Success<*> -> {
                        syncSetupWideEvent.onAccountCreated()
                        syncPixels.fireSignupDirectPixel(source)
                        getDeviceAndFinish()
                    }

                    is Result.Error -> {
                        syncSetupWideEvent.onAccountCreationFailed()
                        _commands.send(
                            Command.ShowError(
                                R.string.sync_create_account_generic_error,
                                result.reason,
                            ),
                        )
                    }
                }
            }

            _viewState.update { it.copy(isSyncing = false) }
        }
    }

    fun onScreenShown() {
        viewModelScope.launch {
            syncSetupWideEvent.onIntroScreenShown()
        }
    }

    fun onSyncWithAnotherDeviceClicked() {
        viewModelScope.launch {
            _commands.send(Command.SyncWithAnotherDevice)
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch {
            _commands.send(Command.AbortSyncing)
        }
    }

    fun onErrorDismissed() {
        viewModelScope.launch {
            _commands.send(Command.AbortSyncing)
        }
    }

    data class ViewState(
        val isSyncing: Boolean = false,
    )

    sealed interface Command {
        data class FinishSyncing(
            val device: ConnectedDevice,
        ) : Command

        data object SyncWithAnotherDevice : Command

        data object AbortSyncing : Command

        data class ShowError(
            @StringRes val message: Int,
            val reason: String? = "",
        ) : Command
    }
}
