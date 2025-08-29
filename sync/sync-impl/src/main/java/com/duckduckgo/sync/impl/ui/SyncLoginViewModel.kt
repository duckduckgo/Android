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
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Companion.POLLING_INTERVAL_EXCHANGE_FLOW
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.ShowError
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncLoginViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object ReadTextCode : Command()
        data object LoginSucess : Command()
        data object Error : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            syncPixels.fireLoginPixel()
            command.send(LoginSucess)
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(Command.Error)
        }
    }

    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            val codeType = syncAccountRepository.parseSyncAuthCode(qrCode)
            when (val result = syncAccountRepository.processCode(codeType)) {
                is Error -> {
                    processError(result)
                }

                is Success -> {
                    if (codeType is SyncAuthCode.Exchange) {
                        pollForRecoveryKey()
                    } else {
                        syncPixels.fireLoginPixel()
                        command.send(LoginSucess)
                    }
                }
            }
        }
    }

    private suspend fun processError(result: Error) {
        when (result.code) {
            ALREADY_SIGNED_IN.code -> R.string.sync_login_authenticated_device_error
            LOGIN_FAILED.code -> R.string.sync_connect_login_error
            CONNECT_FAILED.code -> R.string.sync_connect_generic_error
            CREATE_ACCOUNT_FAILED.code -> R.string.sync_create_account_generic_error
            INVALID_CODE.code -> R.string.sync_invalid_code_error
            else -> null
        }?.let { message ->
            command.send(ShowError(message = message, reason = result.reason))
        }
    }

    private suspend fun pollForRecoveryKey() {
        var polling = true
        while (polling) {
            delay(POLLING_INTERVAL_EXCHANGE_FLOW)
            syncAccountRepository.pollForRecoveryCodeAndLogin()
                .onSuccess { success ->
                    when (success) {
                        is Pending -> return@onSuccess // continue polling
                        is AccountSwitchingRequired -> {
                            polling = false
                            processError(Error(ALREADY_SIGNED_IN.code, "user already signed in"))
                        }
                        LoggedIn -> {
                            polling = false
                            syncPixels.fireLoginPixel()
                            command.send(LoginSucess)
                        }
                    }
                }.onFailure {
                    polling = false
                    processError(it)
                }
        }
    }
}
