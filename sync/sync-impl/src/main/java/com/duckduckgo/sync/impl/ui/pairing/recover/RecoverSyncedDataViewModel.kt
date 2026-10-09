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

package com.duckduckgo.sync.impl.ui.pairing.recover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.sync.impl.auth.AuthPrompt
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.pixels.SyncPixels
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

class RecoverSyncedDataViewModel @AssistedInject constructor(
    @Assisted private val isAuthRequired: Boolean,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val syncPixels: SyncPixels,
) : ViewModel() {
    private val _commands = Channel<Command>()
    val commands = _commands.receiveAsFlow()

    val authPrompts: StateFlow<AuthPrompt?> = deviceAuthenticator.currentPrompt

    private var authJob: Job? = null

    fun onRecoverDataClicked() {
        if (authJob?.isActive == true) return
        syncPixels.fireRecoverSyncDataConfirmed()
        authJob = viewModelScope.launch {
            if (!isAuthRequired) {
                _commands.send(Command.ReadSyncCode)
                return@launch
            }
            when (val response = deviceAuthenticator.authenticate(Request())) {
                is Response.Allowed -> _commands.send(Command.ReadSyncCode)
                is Response.Cancelled -> Unit
                is Response.Failed -> {
                    logcat(WARN) { "Sync: device authentication failed: ${response.reason}" }
                    _commands.send(Command.ShowAuthError)
                }
            }
        }
    }

    sealed interface Command {
        data object ReadSyncCode : Command
        data object ShowAuthError : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(isAuthRequired: Boolean): RecoverSyncedDataViewModel

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
