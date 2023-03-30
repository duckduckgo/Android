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

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class SyncActivityViewModel @Inject constructor(
    private val qrEncoder: QREncoder,
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
    }

    fun getSyncState() {
        viewModelScope.launch {
            updateViewState()
        }
    }

    fun onToggleClicked(isChecked: Boolean) {
        viewModelScope.launch {
            viewState.emit(viewState.value.copy(isDeviceSyncEnabled = isChecked))
            if (isChecked) {
                command.send(LaunchDeviceSetupFlow)
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
}
