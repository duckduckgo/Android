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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SaveRecoveryCodeViewModel.ViewMode.AccountCreated
import com.duckduckgo.sync.impl.ui.SaveRecoveryCodeViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.SetupAccountViewModel.ViewMode.SyncAnotherDevice
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.lang.IllegalStateException
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SaveRecoveryCodeViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { createAccount() }

    private fun createAccount() = viewModelScope.launch(dispatchers.io()) {
        if (syncRepository.isSignedIn()) {
            syncRepository.getRecoveryCode()?.let {
                val newState = AccountCreated(
                    loginQRCode = it,
                    b64RecoveryCode = it,
                )
                viewState.emit(ViewState(newState))
            } ?: viewState.emit(ViewState(ViewMode.Error))
        } else {
            viewState.emit(ViewState(CreatingAccount))
            val result = syncRepository.createAccount()
            when(result) {
                is Error -> TODO()
                is Success -> {
                    syncRepository.getRecoveryCode()?.let {
                        viewState.emit(ViewState(AccountCreated(
                            it,
                            it,
                        )))
                    }?: viewState.emit(ViewState(ViewMode.Error))
                }
            }
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = CreatingAccount
    )

    sealed class ViewMode {
        object CreatingAccount : ViewMode()
        object Error : ViewMode()
        data class AccountCreated(
            val loginQRCode: String,
            val b64RecoveryCode: String,
        ) : ViewMode()
    }

    sealed class Command {
        object Finish : Command()
    }

    fun onBackPressed() {
    }
}
