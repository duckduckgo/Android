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
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PreviousSessionReadyViewModel @AssistedInject constructor(
    @Assisted private val launchSource: String,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val syncPixels: SyncPixels,
    private val syncSetupWideEvent: SyncSetupWideEvent,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    fun onScreenShown() {
        syncPixels.fireAutoRestoreSettingsReadyShown(launchSource)
    }

    fun onResumeClicked() {
        syncPixels.fireAutoRestoreSettingsRestoreTapped(launchSource)
        viewModelScope.launch(dispatchers.io()) {
            syncSetupWideEvent.onSyncRestoreStarted()
            val payload = syncAutoRestoreManager.retrieveRecoveryPayload()
            if (payload != null) {
                _commands.send(Command.SetResumeResult(payload.recoveryCode))
                _commands.send(Command.Close)
            } else {
                _commands.send(Command.ShowError(R.string.sync_simplified_error_dialog_generic_body))
            }
        }
    }

    fun onContinueSetupClicked() {
        syncPixels.fireAutoRestoreSettingsSkipRestoreTapped(launchSource)
        viewModelScope.launch {
            _commands.send(Command.SetContinueSetupResult)
            _commands.send(Command.Close)
        }
    }

    fun onCloseClicked() {
        syncPixels.fireAutoRestoreSettingsCancelled(launchSource)
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    sealed interface Command {
        data class SetResumeResult(
            val recoveryCode: String,
        ) : Command

        data object SetContinueSetupResult : Command

        data object Close : Command

        data class ShowError(
            @StringRes val message: Int,
        ) : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(launchSource: String): PreviousSessionReadyViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val launchSource: String,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(launchSource) as T
            }
        }
    }
}
