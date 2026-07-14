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
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.R.dimen
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncAuthCode.Unknown
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.CodeVersion
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.fireSetupCancelledIfDenied
import com.duckduckgo.sync.impl.pixels.fireSetupFailed
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.FinishWithError
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowMessage
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
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncConnectViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val qrEncoder: QREncoder,
    private val clipboard: Clipboard,
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
    private val syncFeature: SyncFeature,
    private val codeDispatcher: SyncCodeDispatcher,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    // Cached v2 linking code so onCopyCodeClicked can return it for the v2 path. Null on the v1 path.
    private var v2LinkingCode: String? = null

    // Start the session exactly once per ViewModel instance.
    private var sessionStarted = false

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(source: String?): Flow<ViewState> = viewState.onStart {
        pollConnectionKeys(source)
    }

    private fun pollConnectionKeys(source: String?) {
        if (sessionStarted) return
        sessionStarted = true
        viewModelScope.launch(dispatchers.io()) {
            // Don't start a fresh v2 session for a user already signed in via EnterCode while this
            // activity is mid-finish (onStart can re-fire before onLoginSuccess() finishes it).
            if (syncAccountRepository.getAccountInfo().isSignedIn) {
                logcat { "Sync: SyncConnect already signed in; skipping new session" }
                return@launch
            }
            if (shouldUseV2()) {
                startV2Present()
                return@launch
            }
            showQRCode()
            var polling = true
            while (polling) {
                delay(POLLING_INTERVAL_CONNECT_FLOW)
                syncAccountRepository.pollConnectionKeys()
                    .onSuccess { success ->
                        if (!success) return@onSuccess // continue polling
                        syncPixels.fireSignupConnectPixel(source)
                        syncPixels.fireSyncSetupFinishedSuccessfully(SYNC_CONNECT)
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

    private fun shouldUseV2(): Boolean =
        syncFeature.canUseV2ConnectFlow().isEnabled() &&
            syncFeature.canShowV2ConnectCode().isEnabled()

    /** Drive a v2 Presenter session via the dispatcher. Role election may elect this device Host or Joiner. */
    private suspend fun startV2Present() {
        codeDispatcher.presentV2().collect { handleV2Outcome(it) }
    }

    /** Map one v2 [DispatchOutcome] onto this VM's command pipeline. Shared by the Presenter and Scanner paths. */
    private suspend fun handleV2Outcome(outcome: DispatchOutcome) {
        syncPixels.fireSetupFailed(SYNC_CONNECT, outcome)
        syncPixels.fireSetupCancelledIfDenied(SYNC_CONNECT, outcome)
        when (outcome) {
            is DispatchOutcome.LinkingCodeReady -> renderV2QrCode(outcome.linkingCode)
            is DispatchOutcome.HostConfirmationRequested -> command.send(Command.AskHostConfirmation(outcome.peerName, outcome.peerKind))
            is DispatchOutcome.JoinerConfirmationRequested -> command.send(Command.AskJoinerConfirmation(outcome.peerName, outcome.peerKind))
            is DispatchOutcome.LoggedIn -> {
                syncPixels.fireLoginPixel()
                syncPixels.fireSyncSetupFinishedSuccessfully(SYNC_CONNECT, outcome.path, outcome.myRole, outcome.peerKind)
                command.send(LoginSuccess)
            }
            is DispatchOutcome.AlreadyConnected ->
                command.send(Command.ShowV2Error(v2AlreadyPairedError))
            is DispatchOutcome.UpgradeRequired -> {
                logcat { "Sync v2: upgrade required, peer needs protocol v${outcome.codeMajor}" }
                command.send(Command.ShowV2Error(v2UpgradeRequiredError))
            }
            is DispatchOutcome.Failed ->
                command.send(Command.ShowV2Error(outcome.code.toV2PairingError()))
        }
    }

    private suspend fun renderV2QrCode(linkingCode: String) {
        v2LinkingCode = linkingCode
        val bitmap = withContext(dispatchers.io()) {
            qrEncoder.encodeAsBitmap(linkingCode, dimen.qrSizeSmall, dimen.qrSizeSmall)
        }
        viewState.emit(viewState.value.copy(qrCodeBitmap = bitmap))
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
                            fireLoginPixels()
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
            val v2Code = v2LinkingCode
            if (v2Code != null) {
                clipboard.copyToClipboard(v2Code)
                command.send(ShowMessage(R.string.sync_code_copied_message))
                syncPixels.fireSyncSetupCodeCopiedToClipboard(SYNC_CONNECT)
                return@launch
            }
            syncAccountRepository.getConnectQR().getOrNull()?.let { code ->
                logcat { "Sync: code available for sharing manually: $code" }
                clipboard.copyToClipboard(code.rawCode)
                command.send(ShowMessage(R.string.sync_code_copied_message))
                syncPixels.fireSyncSetupCodeCopiedToClipboard(SYNC_CONNECT)
            } ?: command.send(FinishWithError)
        }
    }

    data class ViewState(
        val qrCodeBitmap: Bitmap? = null,
    )

    sealed class Command {
        data object ReadTextCode : Command()
        data object LoginSuccess : Command()
        data class ShowMessage(val messageId: Int) : Command()
        data object FinishWithError : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()

        /** v2 §"Exchange Confirmations": prompt user "Sync your data with [peerName]?". */
        data class AskJoinerConfirmation(val peerName: String?, val peerKind: PeerKind? = null) : Command()

        /** v2 §"Exchange Confirmations": prompt user "Allow [peerName] to join your sync?". */
        data class AskHostConfirmation(val peerName: String?, val peerKind: PeerKind? = null) : Command()

        internal data class ShowV2Error(val content: V2PairingErrorContent) : Command()
    }

    fun onReadTextCodeClicked() {
        viewModelScope.launch {
            command.send(ReadTextCode)
        }
    }

    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            // Route via SyncCodeDispatcher: FF off → Legacy (v1 path unchanged); FF on → v2 codes surface via DispatchOutcome.
            when (val decision = codeDispatcher.route(qrCode)) {
                is RouteDecision.Legacy -> {
                    val codeType = decision.authCode.also { it.onCodeScanned() }
                    when (val result = syncAccountRepository.processCode(codeType)) {
                        is Error -> {
                            processError(result)
                        }

                        is Success -> {
                            if (codeType is Exchange) {
                                pollForRecoveryKey()
                            } else {
                                fireLoginPixels()
                                command.send(LoginSuccess)
                            }
                        }
                    }
                }
                is RouteDecision.V2InProgress -> {
                    logcat { "Sync-CodeDispatch: SyncConnectViewModel observing V2InProgress" }
                    syncPixels.fireBarcodeScannerParseSuccess(SYNC_CONNECT, CodeVersion.V2, decision.codeType)
                    decision.outcomes.collect { handleV2Outcome(it) }
                }
            }
        }
    }

    fun onBarcodeScreenShown() {
        syncPixels.fireSyncBarcodeScreenShown(SYNC_CONNECT)
    }

    fun onUserCancelledWithoutSyncSetup() {
        val reason = if (codeDispatcher.isV2ExchangeUnderway()) {
            CancellationReason.CANCELLED_BEFORE_FINISHED
        } else {
            CancellationReason.SCANNING_CANCELLED
        }
        syncPixels.fireSyncSetupAbandoned(SYNC_CONNECT, reason)
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
            // Manual code entry: EnterCodeViewModel fires the "Setup success" pixel (it has the v2
            // path/role/peer); here we only fire the login pixel to avoid double-counting.
            syncPixels.fireLoginPixel()
            command.send(LoginSuccess)
        }
    }

    fun onJoinerConfirmed() { codeDispatcher.confirmJoiner() }
    fun onJoinerDenied() { codeDispatcher.denyJoiner() }
    fun onHostConfirmed() { codeDispatcher.confirmHost() }
    fun onHostDenied() { codeDispatcher.denyHost() }

    private fun fireLoginPixels() {
        syncPixels.fireLoginPixel()
        syncPixels.fireSyncSetupFinishedSuccessfully(SYNC_CONNECT)
    }

    private fun SyncAuthCode.onCodeScanned() {
        when (this) {
            is Unknown -> syncPixels.fireBarcodeScannerParseError(SYNC_CONNECT, reason = SetupFailureReason.UNRECOGNIZED_CODE)
            else -> syncPixels.fireBarcodeScannerParseSuccess(SYNC_CONNECT, CodeVersion.V1)
        }
    }

    companion object {
        const val POLLING_INTERVAL_CONNECT_FLOW = 5_000L
        const val POLLING_INTERVAL_EXCHANGE_FLOW = 2_000L
    }
}
