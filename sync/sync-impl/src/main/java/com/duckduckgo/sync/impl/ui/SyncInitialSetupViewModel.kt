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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncInitialSetupViewModel
@Inject
constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { updateViewState() }
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
    )

    sealed class Command {
        data class ShowMessage(val message: String) : Command()
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.createAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onStoreRecoveryCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncRepository.storeRecoveryCode()
            updateViewState()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncRepository.removeAccount()
            updateViewState()
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.logout()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.deleteAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun loginAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.login()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onSendBookmarksClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.initialPatch()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            } else {
                command.send(Command.ShowMessage("Bookmarks Sent Successfully"))
            }
            updateViewState()
        }
    }

    fun onReceiveBookmarksClicked(){
        viewModelScope.launch(dispatchers.io()) {
            val result = syncRepository.getAll()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            } else {
                command.send(Command.ShowMessage("Bookmarks Sent Successfully"))
            }
            updateViewState()
        }
    }

    private suspend fun updateViewState() {
        val accountInfo = syncRepository.getAccountInfo()
        viewState.emit(
            viewState.value.copy(
                userId = accountInfo.userId,
                deviceName = accountInfo.deviceName,
                deviceId = accountInfo.deviceId,
                isSignedIn = accountInfo.isSignedIn,
                token = syncRepository.latestToken(),
                primaryKey = accountInfo.primaryKey,
                secretKey = accountInfo.secretKey,
            ),
        )
    }
}
