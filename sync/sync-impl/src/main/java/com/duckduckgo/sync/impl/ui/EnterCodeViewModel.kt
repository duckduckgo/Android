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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.ShowError
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class EnterCodeViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val clipboard: Clipboard,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState

    data class ViewState(
        val code: String = "",
        val authState: AuthState = AuthState.Idle,
    )

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Error : AuthState()
    }

    sealed class Command {
        object LoginSucess : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
    }

    fun onPasteCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val pastedCode = clipboard.pasteFromClipboard()
            viewState.value = viewState.value.copy(code = pastedCode, authState = AuthState.Loading)
            authFlow(pastedCode)
        }
    }

    private suspend fun authFlow(
        pastedCode: String,
    ) {
        val result = syncAccountRepository.processCode(pastedCode)
        when (result) {
            is Result.Success -> command.send(Command.LoginSucess)
            is Result.Error -> {
                when (result.code) {
                    ALREADY_SIGNED_IN.code -> {
                        showError(R.string.sync_login_authenticated_device_error, result.reason)
                    }
                    LOGIN_FAILED.code -> {
                        showError(R.string.sync_connect_login_error, result.reason)
                    }
                    CONNECT_FAILED.code -> {
                        showError(R.string.sync_connect_generic_error, result.reason)
                    }
                    CREATE_ACCOUNT_FAILED.code -> {
                        showError(R.string.sync_create_account_generic_error, result.reason)
                    }
                    INVALID_CODE.code -> {
                        viewState.value = viewState.value.copy(authState = AuthState.Error)
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun showError(
        message: Int,
        reason: String,
    ) {
        command.send(ShowError(message = message, reason = reason))
    }
}
