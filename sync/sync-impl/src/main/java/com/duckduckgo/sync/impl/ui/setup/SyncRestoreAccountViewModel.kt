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

package com.duckduckgo.sync.impl.ui.setup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.RestorationComplete
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.ShowError
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SyncRestoreAccountViewModel @Inject constructor(
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(Unit)
    private var restoreStarted = false

    fun viewState(): Flow<Unit> = viewState.onStart { startRestore() }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object RestorationComplete : Command()
        data object AbortFlow : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
    }

    private fun startRestore() {
        if (restoreStarted) return
        restoreStarted = true
        viewModelScope.launch(dispatchers.io()) {
            val payload = syncAutoRestoreManager.retrieveRecoveryPayload()
            if (payload == null) {
                command.send(ShowError(R.string.sync_general_error))
                return@launch
            }
            val parsedCode = syncAccountRepository.parseSyncAuthCode(payload.recoveryCode)
            when (val result = syncAccountRepository.processCode(parsedCode, existingDeviceId = payload.deviceId)) {
                is Result.Success -> {
                    syncPixels.fireAutoRestoreSuccess(SyncPixelParameters.AUTO_RESTORE_SOURCE_SETTINGS)
                    command.send(RestorationComplete)
                }
                is Result.Error -> {
                    syncPixels.fireAutoRestoreFailure(
                        source = SyncPixelParameters.AUTO_RESTORE_SOURCE_SETTINGS,
                        errorCode = result.code.toString(),
                        errorMessage = result.reason,
                    )
                    command.send(ShowError(R.string.sync_general_error, result.reason))
                }
            }
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            command.send(AbortFlow)
        }
    }
}
