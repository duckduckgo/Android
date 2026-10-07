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
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.auth.AuthPrompt
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AnotherDevicePromptOption
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat

class SyncThisDeviceViewModel @AssistedInject constructor(
    @Assisted private val isAuthRequired: Boolean,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
    private val syncSetupWideEvent: SyncSetupWideEvent,
) : ViewModel() {
    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    val authPrompts: StateFlow<AuthPrompt?> = deviceAuthenticator.currentPrompt

    private var syncThisDeviceJob: Job? = null

    init {
        syncPixels.fireSyncAnotherDevicePromptShown()
        viewModelScope.launch {
            syncSetupWideEvent.onIntroScreenShown()
        }
    }

    fun syncThisDevice(launchSource: String?) {
        if (syncThisDeviceJob?.isActive == true) return
        syncPixels.fireSyncAnotherDevicePromptOptionTapped(AnotherDevicePromptOption.THIS_DEVICE_ONLY)

        syncThisDeviceJob = viewModelScope.launch(dispatchers.io()) {
            if (!authenticate()) return@launch

            _viewState.update { it.copy(isSyncing = true) }
            syncSetupWideEvent.onSyncEnabled()

            suspend fun getDeviceAndFinish() {
                val device = syncAccountRepository.getThisConnectedDevice()
                if (device != null) {
                    _commands.send(Command.FinishSyncing(device))
                } else {
                    syncSetupWideEvent.onAccountCreationFailed()
                    _commands.send(
                        Command.ShowError(R.string.sync_simplified_error_dialog_create_account_body),
                    )
                }
            }

            if (syncAccountRepository.isSignedIn()) {
                getDeviceAndFinish()
            } else {
                when (val result = syncAccountRepository.createAccount()) {
                    is Result.Success<*> -> {
                        syncSetupWideEvent.onAccountCreated()
                        syncPixels.fireSignupDirectPixel(launchSource)
                        getDeviceAndFinish()
                    }

                    is Result.Error -> {
                        syncSetupWideEvent.onAccountCreationFailed()
                        _commands.send(
                            Command.ShowError(
                                R.string.sync_simplified_error_dialog_create_account_body,
                                result.reason,
                            ),
                        )
                    }
                }
            }

            _viewState.update { it.copy(isSyncing = false) }
        }
    }

    fun onSyncWithAnotherDeviceClicked() {
        syncPixels.fireSyncAnotherDevicePromptOptionTapped(AnotherDevicePromptOption.WITH_ANOTHER_DEVICE)
        viewModelScope.launch {
            if (authenticate()) {
                _commands.send(Command.SyncWithAnotherDevice)
            }
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch {
            _commands.send(Command.AbortSyncing)
        }
    }

    fun onErrorDismissed() {
        viewModelScope.launch {
            _commands.send(Command.AbortSyncing)
        }
    }

    private suspend fun authenticate(): Boolean {
        if (!isAuthRequired) return true
        val response = deviceAuthenticator.authenticate(Request()) { event ->
            viewModelScope.launch {
                when (event) {
                    Event.EnrollmentNeeded -> syncSetupWideEvent.onDeviceAuthNotEnrolled()
                    Event.EnrollmentShown -> syncSetupWideEvent.onEnrollDeviceAuthDialogShown()
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
                _commands.send(Command.ShowAuthError)
                false
            }
        }
    }

    data class ViewState(
        val isSyncing: Boolean = false,
    )

    sealed interface Command {
        data class FinishSyncing(
            val device: ConnectedDevice,
        ) : Command

        data object SyncWithAnotherDevice : Command

        data object AbortSyncing : Command

        data class ShowError(
            @StringRes val message: Int,
            val reason: String? = "",
        ) : Command

        data object ShowAuthError : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(isAuthRequired: Boolean): SyncThisDeviceViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val isAuthRequired: Boolean,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(isAuthRequired) as T
            }
        }
    }
}
