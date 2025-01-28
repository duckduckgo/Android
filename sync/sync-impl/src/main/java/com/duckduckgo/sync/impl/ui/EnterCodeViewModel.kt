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
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.SwitchAccountSuccess
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
    private val syncFeature: SyncFeature,
    private val syncPixels: SyncPixels,
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
        data object LoginSuccess : Command()
        data class AskToSwitchAccount(val encodedStringCode: String) : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
        data object SwitchAccountSuccess : Command()
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
        val previousPrimaryKey = syncAccountRepository.getAccountInfo().primaryKey
        when (val result = syncAccountRepository.processCode(pastedCode)) {
            is Result.Success -> {
                val postProcessCodePK = syncAccountRepository.getAccountInfo().primaryKey
                val userSwitchedAccount = previousPrimaryKey.isNotBlank() && previousPrimaryKey != postProcessCodePK
                val commandSuccess = if (userSwitchedAccount) {
                    syncPixels.fireUserSwitchedAccount()
                    SwitchAccountSuccess
                } else {
                    LoginSuccess
                }
                command.send(commandSuccess)
            }
            is Result.Error -> {
                processError(result, pastedCode)
            }
        }
    }

    private suspend fun processError(result: Error, pastedCode: String) {
        if (result.code == ALREADY_SIGNED_IN.code && syncFeature.seamlessAccountSwitching().isEnabled()) {
            command.send(AskToSwitchAccount(pastedCode))
        } else {
            if (result.code == INVALID_CODE.code) {
                viewState.value = viewState.value.copy(authState = AuthState.Error)
                return
            }

            when (result.code) {
                ALREADY_SIGNED_IN.code -> R.string.sync_login_authenticated_device_error
                LOGIN_FAILED.code -> R.string.sync_connect_login_error
                CONNECT_FAILED.code -> R.string.sync_connect_generic_error
                CREATE_ACCOUNT_FAILED.code -> R.string.sync_create_account_generic_error
                INVALID_CODE.code -> R.string.sync_invalid_code_error
                else -> null
            }?.let { message ->
                command.send(
                    ShowError(
                        message = message,
                        reason = result.reason,
                    ),
                )
            }
        }
    }

    fun onUserAcceptedJoiningNewAccount(encodedStringCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            syncPixels.fireUserAcceptedSwitchingAccount()
            val result = syncAccountRepository.logoutAndJoinNewAccount(encodedStringCode)
            if (result is Error) {
                when (result.code) {
                    ALREADY_SIGNED_IN.code -> R.string.sync_login_authenticated_device_error
                    LOGIN_FAILED.code -> R.string.sync_connect_login_error
                    CONNECT_FAILED.code -> R.string.sync_connect_generic_error
                    CREATE_ACCOUNT_FAILED.code -> R.string.sync_create_account_generic_error
                    INVALID_CODE.code -> R.string.sync_invalid_code_error
                    else -> null
                }?.let { message ->
                    command.send(
                        ShowError(message = message, reason = result.reason),
                    )
                }
            } else {
                syncPixels.fireUserSwitchedAccount()
                command.send(SwitchAccountSuccess)
            }
        }
    }

    fun onUserCancelledJoiningNewAccount() {
        syncPixels.fireUserCancelledSwitchingAccount()
    }

    fun onUserAskedToSwitchAccount() {
        syncPixels.fireAskUserToSwitchAccount()
    }
}
