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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowQR
import com.duckduckgo.sync.store.*
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class SyncInternalSettingsViewModel
@Inject
constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStore: SyncStore,
    private val syncEnvDataStore: SyncInternalEnvDataStore,
    private val syncFaviconFetchingStore: FaviconsFetchingStore,
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
        val useDevEnvironment: Boolean = false,
        val environment: String = "",
    )

    sealed class Command {
        data class ShowMessage(val message: String) : Command()
        data object ReadQR : Command()
        data object ReadConnectQR : Command()
        data class ShowQR(val string: String) : Command()
        data object LoginSuccess : Command()
    }

    init {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState()
        }
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.createAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
            startInitialSync()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncStore.clearAll()
            updateViewState()
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val currentDeviceId = syncAccountRepository.getAccountInfo().deviceId
            val result = syncAccountRepository.logout(currentDeviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onDeviceLogoutClicked(deviceId: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.logout(deviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onEnvironmentChanged(devEnvironment: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            syncEnvDataStore.useSyncDevEnvironment = devEnvironment
            updateViewState()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.deleteAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    private fun getConnectedDevices() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
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

    private fun startInitialSync() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
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

    private suspend fun updateViewState() {
        val accountInfo = syncAccountRepository.getAccountInfo()
        viewState.emit(
            viewState.value.copy(
                userId = accountInfo.userId,
                deviceName = accountInfo.deviceName,
                deviceId = accountInfo.deviceId,
                isSignedIn = accountInfo.isSignedIn,
                token = syncAccountRepository.latestToken(),
                primaryKey = accountInfo.primaryKey,
                secretKey = accountInfo.secretKey,
                useDevEnvironment = syncEnvDataStore.useSyncDevEnvironment,
                environment = syncEnvDataStore.syncEnvironmentUrl,
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
            val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull() ?: return@launch
            command.send(ShowQR(recoveryCode.qrCode))
        }
    }

    fun onQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val codeType = syncAccountRepository.parseSyncAuthCode(contents)
            val result = syncAccountRepository.processCode(codeType)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onConnectQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val codeType = syncAccountRepository.parseSyncAuthCode(contents)
            val result = syncAccountRepository.processCode(codeType)
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
            val qrCode = when (val qrCodeResult = syncAccountRepository.getConnectQR()) {
                is Error -> {
                    command.send(ShowMessage("$qrCodeResult"))
                    return@launch
                }

                is Success -> {
                    qrCodeResult.data.qrCode
                }
            }
            updateViewState()
            command.send(ShowQR(qrCode))
            var polling = true
            while (polling) {
                delay(7000)
                when (val result = syncAccountRepository.pollConnectionKeys()) {
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

    fun useRecoveryCode(recoveryCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            authFlow(recoveryCode)
        }
    }

    fun resetFaviconsPrompt() {
        logcat { "Sync-Internal: Reset Favicons Prompt" }
        syncFaviconFetchingStore.isFaviconsFetchingEnabled = false
        syncFaviconFetchingStore.promptShown = false
    }

    private suspend fun authFlow(
        pastedCode: String,
    ) {
        val codeType = syncAccountRepository.parseSyncAuthCode(pastedCode)
        val result = syncAccountRepository.processCode(codeType)
        when (result) {
            is Result.Success -> command.send(Command.LoginSuccess)
            is Result.Error -> {
                command.send(ShowMessage("Something went wrong"))
            }
        }
    }
}
