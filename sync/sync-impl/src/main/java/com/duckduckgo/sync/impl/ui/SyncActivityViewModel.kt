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
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import java.io.File
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.LoadingItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SyncActivityViewModel @Inject constructor(
    private val qrEncoder: QREncoder,
    private val recoveryCodePDF: RecoveryCodePDF,
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart {
        initViewStateThisDevice()
        val syncedDevices = viewState.value.syncedDevices
        viewState.emit(viewState.value.copy(syncedDevices = syncedDevices + LoadingItem))
        updateDevicesList()
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val isDeviceSyncEnabled: Boolean = false,
        val showAccount: Boolean = false,
        val loginQRCode: Bitmap? = null,
        val syncedDevices: List<SyncDeviceListItem> = emptyList(),
    )

    sealed class Command {
        object LaunchDeviceSetupFlow : Command()
        data class AskTurnOffSync(val device: ConnectedDevice) : Command()
        object AskDeleteAccount : Command()
        object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
        data class AskRemoveDevice(val device: ConnectedDevice) : Command()
        data class AskEditDevice(val device: ConnectedDevice) : Command()
    }

    fun getSyncState() {
        viewModelScope.launch {
            initViewStateThisDevice()
            val syncedDevices = viewState.value.syncedDevices
            viewState.emit(viewState.value.copy(syncedDevices = syncedDevices + LoadingItem))
            updateDevicesList()
        }
    }

    fun onToggleClicked(isChecked: Boolean) {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = isChecked))
            when (isChecked) {
                true -> command.send(LaunchDeviceSetupFlow)
                false -> {
                    syncRepository.getThisConnectedDevice()?.let {
                        command.send(AskTurnOffSync(it))
                    } ?: updateAccountViewState()
                }
            }
        }
    }

    private fun updateDevicesList() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.getConnectedDevices()
            if (result is Success) {
                val syncedDevices = result.data.map { SyncedDevice(it) }
                viewState.emit(viewState.value.copy(syncedDevices = syncedDevices))
            } else {
                viewState.value.syncedDevices.filterNot { it is LoadingItem }.apply {
                    viewState.emit(viewState.value.copy(syncedDevices = this))
                }
            }
        }
    }

    private suspend fun initViewStateThisDevice() {
        if (!syncRepository.isSignedIn()) {
            viewState.emit(ViewState(isDeviceSyncEnabled = false, showAccount = false))
            return
        }

        val qrBitmap = withContext(dispatchers.io()) {
            val recoveryCode = syncRepository.getRecoveryCode() ?: return@withContext null
            qrEncoder.encodeAsBitmap(recoveryCode, R.dimen.qrSizeLarge, R.dimen.qrSizeLarge)
        }

        val connectedDevice = syncRepository.getThisConnectedDevice() ?: return

        viewState.emit(
            viewState.value.copy(
                isDeviceSyncEnabled = syncRepository.isSignedIn(),
                showAccount = syncRepository.isSignedIn(),
                loginQRCode = qrBitmap,
                syncedDevices = listOf(SyncedDevice(connectedDevice))
            ),
        )
    }

    private suspend fun updateAccountViewState() {
        if (!syncRepository.isSignedIn()) {
            viewState.emit(
                ViewState(
                    isDeviceSyncEnabled = false,
                    showAccount = false,
                    loginQRCode = null,
                    syncedDevices = emptyList(),
                )
            )
            return
        } else {
            viewState.emit(
                viewState.value.copy(
                    isDeviceSyncEnabled = true,
                    showAccount = true,
                )
            )
        }
    }

    fun onTurnOffSyncConfirmed(connectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            viewState.emit(viewState.value.copy(showAccount = false))
            when (syncRepository.logout(connectedDevice.deviceId)) {
                is Error -> {
                    updateAccountViewState()
                }
                is Success -> {
                    updateAccountViewState()
                }
            }
        }
    }

    fun onTurnOffSyncCancelled() {
        viewModelScope.launch {
            updateAccountViewState()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = false))
            command.send(AskDeleteAccount)
        }
    }

    fun onDeleteAccountConfirmed() {
        viewModelScope.launch(dispatchers.io()) {
            viewState.emit(viewState.value.copy(showAccount = false))
            when (syncRepository.deleteAccount()) {
                is Error -> {
                    updateAccountViewState()
                }
                is Success -> {
                    updateAccountViewState()
                }
            }
        }
    }

    fun onDeleteAccountCancelled() {
        viewModelScope.launch {
            updateAccountViewState()
        }
    }

    fun onSaveRecoveryCodeClicked() {
        viewModelScope.launch {
            command.send(CheckIfUserHasStoragePermission)
        }
    }

    fun generateRecoveryCode(viewContext: Context) {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCodeB64 = syncRepository.getRecoveryCode() ?: return@launch
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
            val syncingDeviceList = viewState.value.syncedDevices.map {
                if (it is SyncedDevice && it.device.deviceId == device.deviceId) {
                    it.copy(loading = true)
                } else {
                    it
                }
            }
            viewState.emit(viewState.value.copy(syncedDevices = syncingDeviceList))
            val result = syncRepository.logout(device.deviceId)
            when (result) {
                is Error -> viewState.emit(viewState.value.copy(syncedDevices = oldList))
                is Success -> updateDevicesList()
            }
        }
    }

    fun onDeviceEdited(editedConnectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            val oldList = viewState.value.syncedDevices
            val syncingDeviceList = viewState.value.syncedDevices.map {
                if (it is SyncedDevice && it.device.deviceId == editedConnectedDevice.deviceId) {
                    it.copy(loading = true)
                } else {
                    it
                }
            }
            viewState.emit(viewState.value.copy(syncedDevices = syncingDeviceList))
            val result = syncRepository.renameDevice(editedConnectedDevice)
            when (result) {
                is Error -> viewState.emit(viewState.value.copy(syncedDevices = oldList))
                is Success -> updateDevicesList()
            }
        }
    }
}
