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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditDeviceViewModel @AssistedInject constructor(
    @Assisted device: ConnectedDevice,
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _viewState = MutableStateFlow(
        ViewState(
            device = device,
            isEditingName = false,
        ),
    )
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    fun onEditDeviceName() {
        viewModelScope.launch {
            _commands.send(Command.AskEditDevice)
        }
    }

    fun confirmNewDeviceName(newName: String) {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.update { it.copy(isEditingName = true) }
            val editedDevice = viewState.value.device.copy(deviceName = newName)
            when (val result = syncAccountRepository.renameDevice(editedDevice)) {
                is Success -> {
                    _viewState.update { it.copy(device = editedDevice) }
                }

                is Error -> {
                    _commands.send(Command.ResetTurnOffSyncToggle)
                    _commands.send(Command.ShowError(R.string.sync_edit_device_error, result.reason))
                }
            }
            _viewState.update { it.copy(isEditingName = false) }
        }
    }

    fun onTurnOffSync() {
        viewModelScope.launch {
            _commands.send(Command.AskTurnOffSync)
        }
    }

    fun onTurnOffSyncConfirmed() {
        viewModelScope.launch {
            _commands.send(Command.SetTurnOffSyncResult)
            _commands.send(Command.Close)
        }
    }

    fun onTurnOffSyncCanceled() {
        viewModelScope.launch {
            _commands.send(Command.ResetTurnOffSyncToggle)
        }
    }

    fun onRemoveDevice() {
        viewModelScope.launch {
            _commands.send(Command.AskRemoveDevice)
        }
    }

    fun onRemoveDeviceConfirmed() {
        viewModelScope.launch {
            _commands.send(Command.SetRemoveDeviceResult)
            _commands.send(Command.Close)
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    data class ViewState(
        val device: ConnectedDevice,
        val isEditingName: Boolean,
    )

    sealed interface Command {
        data object AskEditDevice : Command

        data object SetEditDeviceResult : Command

        data object AskRemoveDevice : Command

        data object SetRemoveDeviceResult : Command

        data object AskTurnOffSync : Command

        data object ResetTurnOffSyncToggle : Command

        data object SetTurnOffSyncResult : Command

        data class ShowError(
            @StringRes val message: Int,
            val reason: String = "",
        ) : Command

        data object Close : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(device: ConnectedDevice): EditDeviceViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val device: ConnectedDevice,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(device) as T
            }
        }
    }
}
