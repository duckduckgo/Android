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

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.DeviceConnected
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.LoadingItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import java.io.File
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class SyncActivityViewModel @Inject constructor(
    private val qrEncoder: QREncoder,
    private val recoveryCodePDF: RecoveryCodePDF,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStateMonitor: SyncStateMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun commands(): Flow<Command> = command.receiveAsFlow()

    fun viewState(): Flow<ViewState> =
        viewState.onStart {
            observeState()
        }.flowOn(dispatchers.io())

    private fun observeState() {
        syncStateMonitor.syncState().onEach { syncState ->
            val state = if (syncState == OFF) {
                signedOutState()
            } else {
                signedInState()
            }
            viewState.value = state
        }.onStart {
            initViewStateThisDeviceState()
            fetchRemoteDevices()
        }
            .launchIn(viewModelScope)
    }

    private suspend fun signedInState(): ViewState {
        val qrBitmap = withContext(dispatchers.io()) {
            val recoveryCode = syncAccountRepository.getRecoveryCode() ?: return@withContext null
            qrEncoder.encodeAsBitmap(recoveryCode, R.dimen.qrSizeLarge, R.dimen.qrSizeLarge)
        } ?: return signedOutState()

        val connectedDevices = viewState.value.syncedDevices
        val syncedDevices = connectedDevices.ifEmpty {
            val thisDevice = syncAccountRepository.getThisConnectedDevice() ?: return signedOutState()
            listOf(SyncedDevice(thisDevice))
        }

        return ViewState(
            syncToggleState = syncAccountRepository.isSignedIn(),
            showAccount = syncAccountRepository.isSignedIn(),
            loginQRCode = qrBitmap,
            syncedDevices = syncedDevices,
        )
    }

    private suspend fun initViewStateThisDeviceState() {
        val state = withContext(dispatchers.io()) {
            if (!syncAccountRepository.isSignedIn()) {
                signedOutState()
            } else {
                signedInState()
            }
        }

        viewState.value = state
    }

    data class ViewState(
        val syncToggleState: Boolean = false,
        val showAccount: Boolean = false,
        val loginQRCode: Bitmap? = null,
        val syncedDevices: List<SyncDeviceListItem> = emptyList(),
    )

    sealed class Command {
        object ScanQRCode : Command()
        object EnterTextCode : Command()
        object CreateAccount : Command()
        object RecoverSyncData : Command()
        object ShowTextCode : Command()
        object DeviceConnected : Command()
        data class AskTurnOffSync(val device: ConnectedDevice) : Command()
        object AskDeleteAccount : Command()
        object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
        data class AskRemoveDevice(val device: ConnectedDevice) : Command()
        data class AskEditDevice(val device: ConnectedDevice) : Command()
    }

    fun onScanQRCodeClicked() {
        viewModelScope.launch {
            command.send(Command.ScanQRCode)
        }
    }

    fun onEnterTextCodeClicked() {
        viewModelScope.launch {
            command.send(Command.EnterTextCode)
        }
    }

    fun onInitializeSync() {
        viewModelScope.launch {
            viewState.value = viewState.value.toggle(true)
            command.send(CreateAccount)
        }
    }

    fun onRecoverYourSyncedData() {
        viewModelScope.launch {
            command.send(Command.RecoverSyncData)
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            command.send(Command.DeviceConnected)
        }
    }

    fun onTurnOffClicked() {
        viewModelScope.launch {
            syncAccountRepository.getThisConnectedDevice()?.let {
                command.send(AskTurnOffSync(it))
            } ?: showAccountDetailsIfNeeded()

            viewState.value = viewState.value.toggle(false)
        }
    }

    fun onShowTextCodeClicked() {
        viewModelScope.launch {
            command.send(Command.ShowTextCode)
        }
    }

    private suspend fun fetchRemoteDevices() {
        viewState.value = viewState.value.showDeviceListItemLoading()
        val result = withContext(dispatchers.io()) {
            syncAccountRepository.getConnectedDevices()
        }
        if (result is Success) {
            val newState = viewState.value.hideDeviceListItemLoading().setDevices(result.data.map { SyncedDevice(it) })
            viewState.value = newState
        } else {
            viewState.value = viewState.value.hideDeviceListItemLoading()
        }
    }

    fun onTurnOffSyncConfirmed(connectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            viewState.value = viewState.value.hideAccount()
            when (syncAccountRepository.logout(connectedDevice.deviceId)) {
                is Error -> {
                    viewState.value = viewState.value.toggle(true).showAccount()
                }

                is Success -> {
                    viewState.value = signedOutState()
                }
            }
        }
    }

    fun onTurnOffSyncCancelled() {
        showAccountDetailsIfNeeded()
    }

    fun onConnectionCancelled() {
        showAccountDetailsIfNeeded()
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch {
            viewState.value = viewState.value.toggle(false)
            command.send(AskDeleteAccount)
        }
    }

    fun onDeleteAccountConfirmed() {
        viewModelScope.launch(dispatchers.io()) {
            viewState.value = viewState.value.hideAccount()
            when (syncAccountRepository.deleteAccount()) {
                is Error -> {
                    viewState.value = viewState.value.toggle(true).showAccount()
                }

                is Success -> {
                    viewState.value = signedOutState()
                }
            }
        }
    }

    fun onDeleteAccountCancelled() {
        showAccountDetailsIfNeeded()
    }

    fun onSaveRecoveryCodeClicked() {
        viewModelScope.launch {
            command.send(CheckIfUserHasStoragePermission)
        }
    }

    fun generateRecoveryCode(viewContext: Context) {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCodeB64 = syncAccountRepository.getRecoveryCode() ?: return@launch
            val generateRecoveryCodePDF = recoveryCodePDF.generateAndStoreRecoveryCodePDF(viewContext, recoveryCodeB64)
            command.send(RecoveryCodePDFSuccess(generateRecoveryCodePDF))
        }
    }

    fun onEditDeviceClicked(device: ConnectedDevice) {
        viewModelScope.launch {
            command.send(AskEditDevice(device))
        }
    }

    fun onRemoveDeviceClicked(device: ConnectedDevice) {
        viewModelScope.launch {
            command.send(AskRemoveDevice(device))
        }
    }

    fun onRemoveDeviceConfirmed(device: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            val oldList = viewState.value.syncedDevices
            viewState.value = viewState.value.showDeviceListItemLoading(device)
            when (syncAccountRepository.logout(device.deviceId)) {
                is Error -> {
                    viewState.value = viewState.value.setDevices(oldList)
                }

                is Success -> fetchRemoteDevices()
            }
        }
    }

    fun onDeviceEdited(editedConnectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            val oldList = viewState.value.syncedDevices
            viewState.value = viewState.value.showDeviceListItemLoading(editedConnectedDevice)
            when (syncAccountRepository.renameDevice(editedConnectedDevice)) {
                is Error -> {
                    viewState.value = viewState.value.setDevices(oldList)
                }

                is Success -> fetchRemoteDevices()
            }
        }
    }

    fun onDeviceConnected() {
        viewModelScope.launch {
            fetchRemoteDevices()
        }
    }

    private fun showAccountDetailsIfNeeded() {
        viewModelScope.launch(dispatchers.io()) {
            if (syncAccountRepository.isSignedIn()) {
                viewState.value = viewState.value.toggle(true).showAccount()
            } else {
                viewState.value = signedOutState()
            }
        }
    }

    private fun signedOutState(): ViewState = ViewState()
    private fun ViewState.isSignedInState() = this.loginQRCode != null && this.showAccount
    private fun ViewState.toggle(isChecked: Boolean) = copy(syncToggleState = isChecked)
    private fun ViewState.setDevices(devices: List<SyncDeviceListItem>) = copy(syncedDevices = devices)
    private fun ViewState.hideDeviceListItemLoading() = copy(syncedDevices = syncedDevices.filterNot { it is LoadingItem })
    private fun ViewState.showDeviceListItemLoading() = copy(syncedDevices = syncedDevices + LoadingItem)
    private fun ViewState.showDeviceListItemLoading(updatingDevice: ConnectedDevice): ViewState {
        return copy(
            syncedDevices = syncedDevices.map {
                if (it is SyncedDevice && it.device.deviceId == updatingDevice.deviceId) {
                    it.copy(loading = true)
                } else {
                    it
                }
            },
        )
    }

    private fun ViewState.showAccount() = copy(showAccount = true)
    private fun ViewState.hideAccount() = copy(showAccount = false)
}
