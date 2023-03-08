/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ReadQR
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowQR
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncInitialSetupViewModel
@Inject
constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { getConnectedDevices() }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val userId: String = "",
        val deviceName: String = "",
        val deviceId: String = "",
        val token: String = "",
        val isSignedIn: Boolean = false,
        val primaryKey: String = "",
        val secretKey: String = "",
        val protectedEncryptionKey: String = "",
        val passwordHash: String = "",
        val connectedDevices: List<ConnectedDevice> = emptyList(),
    )

    sealed class Command {
        data class ShowMessage(val message: String) : Command()
        object ReadQR : Command()
        object ReadConnectQR : Command()
        data class ShowQR(val string: String) : Command()
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.createAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onStoreRecoveryCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncRepository.storeRecoveryCode()
            updateViewState()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncRepository.removeAccount()
            updateViewState()
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val currentDeviceId = syncRepository.getAccountInfo().deviceId
            val result = syncRepository.logout(currentDeviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onDeviceLogoutClicked(deviceId: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.logout(deviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.deleteAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun loginAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.login()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
            updateViewState()
        }
    }

    private fun getConnectedDevices() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncRepository.getConnectedDevices()) {
                is Error -> command.send(Command.ShowMessage(connectedDevices.reason))
                is Success -> {
                    viewState.emit(
                        viewState.value.copy(
                            connectedDevices = connectedDevices.data,
                        ),
                    )
                }
            }
            updateViewState()
        }
    }

    fun onSendBookmarksClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.initialPatch()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            } else {
                command.send(Command.ShowMessage("Bookmarks Sent Successfully"))
            }
            updateViewState()
        }
    }

    fun onReceiveBookmarksClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.getAll()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            } else {
                command.send(Command.ShowMessage("Bookmarks Sent Successfully"))
            }
            updateViewState()
        }
    }

    private suspend fun updateViewState() {
        val accountInfo = syncRepository.getAccountInfo()
        viewState.emit(
            viewState.value.copy(
                userId = accountInfo.userId,
                deviceName = accountInfo.deviceName,
                deviceId = accountInfo.deviceId,
                isSignedIn = accountInfo.isSignedIn,
                token = syncRepository.latestToken(),
                primaryKey = accountInfo.primaryKey,
                secretKey = accountInfo.secretKey,
            ),
        )
    }

    fun onReadQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(ReadQR)
        }
    }

    fun onShowQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCode = syncRepository.getRecoveryCode() ?: return@launch
            command.send(ShowQR(recoveryCode))
        }
    }

    fun onQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.login(contents)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onConnectQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.connectDevice(contents)
            when (result) {
                is Error -> {
                    command.send(Command.ShowMessage("$result"))
                }
                is Success -> {
                    command.send(Command.ShowMessage("${result.data}"))
                    updateViewState()
                }
            }
        }
    }

    fun onConnectStart() {
        viewModelScope.launch(dispatchers.io()) {
            val qrCode = when (val qrCodeResult = syncRepository.getConnectQR()) {
                is Error -> {
                    command.send(ShowMessage("$qrCodeResult"))
                    return@launch
                }
                is Success -> qrCodeResult.data
            }
            updateViewState()
            command.send(ShowQR(qrCode))
            var polling = true
            while (polling) {
                delay(7000)
                when (val result = syncRepository.pollConnectionKeys()) {
                    is Error -> {
                        command.send(Command.ShowMessage("$result"))
                    }
                    is Success -> {
                        command.send(Command.ShowMessage(result.data.toString()))
                        polling = false
                        updateViewState()
                    }
                }
            }
        }
    }

    fun onReadConnectQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            viewModelScope.launch(dispatchers.io()) {
                command.send(ReadConnectQR)
            }
        }
    }
}
