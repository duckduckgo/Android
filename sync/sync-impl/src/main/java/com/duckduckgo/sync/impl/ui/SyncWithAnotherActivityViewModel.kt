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
import com.duckduckgo.sync.impl.CodeType.EXCHANGE
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.R.dimen
import com.duckduckgo.sync.impl.R.string
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Companion.POLLING_INTERVAL_EXCHANGE_FLOW
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.FinishWithError
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.SwitchAccountSuccess
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract.EnterCodeContractOutput
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
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class SyncWithAnotherActivityViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val qrEncoder: QREncoder,
    private val clipboard: Clipboard,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
    private val syncFeature: SyncFeature,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private var barcodeContents: AuthCode? = null

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart {
        startExchangeProcess()
    }

    private fun startExchangeProcess() {
        viewModelScope.launch(dispatchers.io()) {
            showQRCode()
            var polling = syncFeature.exchangeKeysToSyncWithAnotherDevice().isEnabled()
            while (polling) {
                delay(POLLING_INTERVAL_EXCHANGE_FLOW)
                syncAccountRepository.pollSecondDeviceExchangeAcknowledgement()
                    .onSuccess { success ->
                        if (!success) return@onSuccess // continue polling
                        command.send(Command.LoginSuccess)
                        polling = false
                    }.onFailure {
                        when (it.code) {
                            CONNECT_FAILED.code, LOGIN_FAILED.code -> {
                                command.send(Command.ShowError(string.sync_connect_login_error, it.reason))
                                polling = false
                            }
                        }
                    }
            }
        }
    }

    private suspend fun showQRCode() {
        // get the code as a Result, and pair it with the type of code we're dealing with
        val result = if (!syncFeature.exchangeKeysToSyncWithAnotherDevice().isEnabled()) {
            syncAccountRepository.getRecoveryCode()
        } else {
            syncAccountRepository.generateExchangeInvitationCode()
        }

        result.onSuccess { authCode ->
            barcodeContents = authCode

            val qrBitmap = withContext(dispatchers.io()) {
                qrEncoder.encodeAsBitmap(authCode.qrCode, dimen.qrSizeSmall, dimen.qrSizeSmall)
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
            barcodeContents?.let { contents ->
                clipboard.copyToClipboard(contents.rawCode)
                command.send(ShowMessage(string.sync_code_copied_message))
            } ?: command.send(FinishWithError)
        }
    }

    data class ViewState(
        val qrCodeBitmap: Bitmap? = null,
    )

    sealed class Command {
        object ReadTextCode : Command()
        object LoginSuccess : Command()
        object SwitchAccountSuccess : Command()
        data class ShowMessage(val messageId: Int) : Command()
        object FinishWithError : Command()
        data class ShowError(
            @StringRes val message: Int,
            val reason: String = "",
        ) : Command()

        data class AskToSwitchAccount(val encodedStringCode: String) : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
        }
    }

    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            val previousPrimaryKey = syncAccountRepository.getAccountInfo().primaryKey
            val codeType = syncAccountRepository.getCodeType(qrCode)
            when (val result = syncAccountRepository.processCode(qrCode)) {
                is Error -> {
                    Timber.w("Sync: error processing code ${result.reason}")
                    emitError(result, qrCode)
                }

                is Success -> {
                    if (codeType == EXCHANGE) {
                        pollForRecoveryKey(previousPrimaryKey = previousPrimaryKey, qrCode = qrCode)
                    } else {
                        onLoginSuccess(previousPrimaryKey)
                    }
                }
            }
        }
    }

    private suspend fun onLoginSuccess(previousPrimaryKey: String) {
        val postProcessCodePK = syncAccountRepository.getAccountInfo().primaryKey
        syncPixels.fireLoginPixel()
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
        qrCode: String,
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
                            is LoggedIn -> {
                                polling = false
                                onLoginSuccess(previousPrimaryKey)
                            }
                        }
                    }.onFailure {
                        polling = false
                        emitError(it, qrCode)
                    }
            }
        }
    }

    private suspend fun emitError(result: Error, qrCode: String) {
        if (result.code == ALREADY_SIGNED_IN.code && syncFeature.seamlessAccountSwitching().isEnabled()) {
            command.send(AskToSwitchAccount(qrCode))
        } else {
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
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            syncPixels.fireLoginPixel()
            command.send(LoginSuccess)
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
                syncPixels.fireLoginPixel()
                syncPixels.fireUserSwitchedAccount()
                command.send(SwitchAccountSuccess)
            }
        }
    }

    fun onEnterCodeResult(result: EnterCodeContractOutput) {
        viewModelScope.launch {
            when (result) {
                EnterCodeContractOutput.Error -> {}
                EnterCodeContractOutput.LoginSuccess -> {
                    syncPixels.fireLoginPixel()
                    command.send(LoginSuccess)
                }
                EnterCodeContractOutput.SwitchAccountSuccess -> {
                    syncPixels.fireLoginPixel()
                    command.send(SwitchAccountSuccess)
                }
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
