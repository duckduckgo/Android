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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.auth.AuthPrompt
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.auth.SyncDeviceAuthenticator
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationSource
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat

class PreviousSessionReadyViewModel @AssistedInject constructor(
    @Assisted private val launchSource: String,
    @Assisted private val isAuthRequired: Boolean,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val deviceAuthenticator: SyncDeviceAuthenticator,
    private val syncPixels: SyncPixels,
    private val syncSetupWideEvent: SyncSetupWideEvent,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    val authPrompts: StateFlow<AuthPrompt?> = deviceAuthenticator.currentPrompt

    private var authenticatedActionJob: Job? = null

    fun onScreenShown() {
        syncPixels.fireAutoRestoreSettingsReadyShown(launchSource)
    }

    fun onResumeClicked() {
        if (authenticatedActionJob?.isActive == true) return
        syncPixels.fireAutoRestoreSettingsRestoreTapped(launchSource)
        authenticatedActionJob = viewModelScope.launch(dispatchers.io()) {
            if (!authenticate()) return@launch

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
        if (authenticatedActionJob?.isActive == true) return
        syncPixels.fireAutoRestoreSettingsSkipRestoreTapped(launchSource)
        authenticatedActionJob = viewModelScope.launch {
            if (!authenticate()) return@launch

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

    private suspend fun authenticate(): Boolean {
        if (!isAuthRequired) return true
        val response = deviceAuthenticator.authenticate(AuthenticationSource.SETTINGS_NOT_SYNCED, Request()) { event ->
            viewModelScope.launch {
                when (event) {
                    Event.EnrollmentNeeded -> syncSetupWideEvent.onDeviceAuthNotEnrolled()
                    Event.EnrollmentShown -> Unit
                    Event.VerificationShown -> Unit
                }
            }
        }
        return when (response) {
            is Response.Allowed -> {
                syncSetupWideEvent.onUserAuthSuccess()
                true
            }

            is Response.Cancelled -> false

            is Response.Failed -> {
                logcat(WARN) { "Sync: device authentication failed: ${response.reason}" }
                _commands.send(Command.ShowError(R.string.sync_simplified_error_dialog_generic_body))
                false
            }
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
        fun create(
            launchSource: String,
            isAuthRequired: Boolean,
        ): PreviousSessionReadyViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val launchSource: String,
            private val isAuthRequired: Boolean,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(launchSource, isAuthRequired) as T
            }
        }
    }
}
