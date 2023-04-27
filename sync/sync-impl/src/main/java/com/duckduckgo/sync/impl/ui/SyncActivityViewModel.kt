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
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class SyncActivityViewModel @Inject constructor(
    private val qrEncoder: QREncoder,
    private val recoveryCodePDF: RecoveryCodePDF,
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { updateViewState() }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val isDeviceSyncEnabled: Boolean = false,
        val showAccount: Boolean = false,
        val loginQRCode: Bitmap? = null,
    )

    sealed class Command {
        object LaunchDeviceSetupFlow : Command()
        object AskTurnOffSync : Command()
        object AskDeleteAccount : Command()
        object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
    }

    fun getSyncState() {
        viewModelScope.launch {
            updateViewState()
        }
    }

    fun onToggleClicked(isChecked: Boolean) {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = isChecked))
            when (isChecked) {
                true -> command.send(LaunchDeviceSetupFlow)
                false -> command.send(AskTurnOffSync)
            }
        }
    }

    private suspend fun updateViewState() {
        val qrBitmap = withContext(dispatchers.io()) {
            val recoveryCode = syncRepository.getRecoveryCode() ?: return@withContext null
            qrEncoder.encodeAsBitmap(recoveryCode, R.dimen.qrSizeLarge, R.dimen.qrSizeLarge)
        }
        viewState.emit(
            viewState.value.copy(
                isDeviceSyncEnabled = syncRepository.isSignedIn(),
                showAccount = syncRepository.isSignedIn(),
                loginQRCode = qrBitmap,
            ),
        )
    }

    fun onTurnOffSyncConfirmed() {
        viewModelScope.launch(dispatchers.io()) {
            viewState.emit(viewState.value.copy(showAccount = false))
            val deviceId = syncRepository.getThisConnectedDevice().deviceId
            when (syncRepository.logout(deviceId)) {
                is Error -> {
                    updateViewState()
                }
                is Success -> {
                    updateViewState()
                }
            }
        }
    }

    fun onTurnOffSyncCancelled() {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = true))
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
                    Timber.i("deleteAccount failed")
                    updateViewState()
                }
                is Success -> {
                    Timber.i("deleteAccount success")
                    updateViewState()
                }
            }
        }
    }

    fun onDeleteAccountCancelled() {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = true))
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
}
