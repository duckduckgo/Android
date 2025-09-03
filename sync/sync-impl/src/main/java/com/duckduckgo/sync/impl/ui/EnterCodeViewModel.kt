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
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.SwitchAccountSuccess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Companion.POLLING_INTERVAL_EXCHANGE_FLOW
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private var codeType: Code = Code.RECOVERY_CODE

    fun viewState(): Flow<ViewState> = viewState

    data class ViewState(
        val code: String = "",
        val authState: AuthState = AuthState.Idle,
    )

    sealed class AuthState {
        data object Idle : AuthState()
        data object Loading : AuthState()
        data object Error : AuthState()
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
        val codeType = syncAccountRepository.parseSyncAuthCode(pastedCode).also { it.onCodePasted() }
        when (val result = syncAccountRepository.processCode(codeType)) {
            is Result.Success -> {
                if (codeType is SyncAuthCode.Exchange) {
                    pollForRecoveryKey(previousPrimaryKey = previousPrimaryKey, code = pastedCode)
                } else {
                    onLoginSuccess(previousPrimaryKey)
                }
            }
            is Result.Error -> {
                processError(result, pastedCode)
            }
        }
    }

    private suspend fun onLoginSuccess(previousPrimaryKey: String) {
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

    private fun pollForRecoveryKey(
        previousPrimaryKey: String,
        code: String,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            var polling = true
            while (polling) {
                delay(POLLING_INTERVAL_EXCHANGE_FLOW)
                syncAccountRepository.pollForRecoveryCodeAndLogin()
                    .onSuccess { success ->
                        when (success) {
                            is Pending -> return@onSuccess // continue polling
                            is AccountSwitchingRequired -> {
                                polling = false
                                command.send(AskToSwitchAccount(success.recoveryCode))
                            }
                            LoggedIn -> {
                                polling = false
                                onLoginSuccess(previousPrimaryKey)
                            }
                        }
                    }.onFailure {
                        when (it.code) {
                            CONNECT_FAILED.code, LOGIN_FAILED.code -> {
                                polling = false
                                processError(result = it, pastedCode = code)
                            }
                        }
                    }
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
                viewState.value = viewState.value.copy(authState = AuthState.Idle)
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
                viewState.value = viewState.value.copy(authState = AuthState.Idle)
            } else {
                syncPixels.fireUserSwitchedAccount()
                command.send(SwitchAccountSuccess)
            }
        }
    }

    fun onUserCancelledJoiningNewAccount() {
        viewModelScope.launch(dispatchers.io()) {
            syncPixels.fireUserCancelledSwitchingAccount()
            viewState.value = viewState.value.copy(authState = AuthState.Idle)
        }
    }

    fun onUserAskedToSwitchAccount() {
        syncPixels.fireAskUserToSwitchAccount()
    }

    fun onEnterManualCodeScreenShown(codeType: Code) {
        this.codeType = codeType
        syncPixels.fireSyncSetupManualCodeScreenShown(codeType.asScreenType())
    }

    private fun SyncAuthCode.onCodePasted() {
        when (this) {
            is SyncAuthCode.Unknown -> syncPixels.fireSyncSetupCodePastedParseFailure(codeType.asScreenType())
            else -> syncPixels.fireSyncSetupCodePastedParseSuccess(codeType.asScreenType())
        }
    }

    private fun Code.asScreenType(): ScreenType {
        return when (this) {
            Code.RECOVERY_CODE -> ScreenType.SYNC_EXCHANGE
            Code.CONNECT_CODE -> ScreenType.SYNC_CONNECT
        }
    }
}
