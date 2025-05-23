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

import android.graphics.Bitmap
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
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.R.dimen
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.FinishWithError
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowMessage
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class SyncConnectViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val qrEncoder: QREncoder,
    private val clipboard: Clipboard,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(source: String?): Flow<ViewState> = viewState.onStart {
        pollConnectionKeys(source)
    }

    private fun pollConnectionKeys(source: String?) {
        viewModelScope.launch(dispatchers.io()) {
            showQRCode()
            var polling = true
            while (polling) {
                delay(POLLING_INTERVAL_CONNECT_FLOW)
                syncAccountRepository.pollConnectionKeys()
                    .onSuccess { success ->
                        if (!success) return@onSuccess // continue polling
                        syncPixels.fireSignupConnectPixel(source)
                        command.send(LoginSuccess)
                        polling = false
                    }.onFailure {
                        when (it.code) {
                            CONNECT_FAILED.code, LOGIN_FAILED.code -> {
                                command.send(ShowError(R.string.sync_connect_login_error, it.reason))
                                polling = false
                            }
                        }
                    }
            }
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
                            processError(Error(ALREADY_SIGNED_IN.code, success.recoveryCode))
                        }
                        is LoggedIn -> {
                            polling = false
                            syncPixels.fireLoginPixel()
                            command.send(LoginSuccess)
                        }
                    }
                }.onFailure {
                    polling = false
                    processError(it)
                }
        }
    }

    private suspend fun showQRCode() {
        syncAccountRepository.getConnectQR()
            .onSuccess { code ->
                val qrBitmap = withContext(dispatchers.io()) {
                    qrEncoder.encodeAsBitmap(code.qrCode, dimen.qrSizeSmall, dimen.qrSizeSmall)
                }
                viewState.emit(viewState.value.copy(qrCodeBitmap = qrBitmap))
            }.onFailure {
                command.send(Command.FinishWithError)
            }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(FinishWithError)
        }
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncAccountRepository.getConnectQR().getOrNull()?.let { code ->
                logcat { "Sync: code available for sharing manually: $code" }
                clipboard.copyToClipboard(code.rawCode)
                command.send(ShowMessage(R.string.sync_code_copied_message))
            } ?: command.send(FinishWithError)
        }
    }

    data class ViewState(
        val qrCodeBitmap: Bitmap? = null,
    )

    sealed class Command {
        object ReadTextCode : Command()
        object LoginSuccess : Command()
        data class ShowMessage(val messageId: Int) : Command()
        object FinishWithError : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
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
                    if (codeType is Exchange) {
                        pollForRecoveryKey()
                    } else {
                        syncPixels.fireLoginPixel()
                        command.send(LoginSuccess)
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

    fun onLoginSuccess() {
        viewModelScope.launch {
            syncPixels.fireLoginPixel()
            command.send(LoginSuccess)
        }
    }

    companion object {
        const val POLLING_INTERVAL_CONNECT_FLOW = 5_000L
        const val POLLING_INTERVAL_EXCHANGE_FLOW = 2_000L
    }
}
