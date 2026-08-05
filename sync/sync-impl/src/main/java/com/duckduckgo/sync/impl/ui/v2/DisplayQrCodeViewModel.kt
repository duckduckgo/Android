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

package com.duckduckgo.sync.impl.ui.v2

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_EXCHANGE
import com.duckduckgo.sync.impl.pixels.fireSetupCancelledIfDenied
import com.duckduckgo.sync.impl.pixels.fireSetupFailed
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.V1PairingErrorContent
import com.duckduckgo.sync.impl.ui.V2PairingErrorContent
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion
import com.duckduckgo.sync.impl.ui.toV1PairingError
import com.duckduckgo.sync.impl.ui.toV2PairingError
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class DisplayQrCodeViewModel @AssistedInject constructor(
    @Assisted private val syncEntryPoint: SyncEntryPoint,
    @Assisted private val launchSource: String?,
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val pixels: SyncPixels,
    private val syncFeature: SyncFeature,
    private val qrEncoder: QREncoder,
    private val clipboard: ClipboardInteractor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    internal val commands = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    @Volatile
    private var isSignedIn = false

    private val syncType: ScreenType
        get() = if (isSignedIn) SYNC_EXCHANGE else SYNC_CONNECT

    init {
        viewModelScope.launch(dispatchers.io()) {
            isSignedIn = accountRepository.getAccountInfo().isSignedIn
            when (protocolVersion()) {
                ProtocolVersion.V1 -> startV1Presentation()
                ProtocolVersion.V2 -> startV2Presentation()
            }
        }
    }

    fun onHostConfirmed() {
        codeDispatcher.confirmHost()
    }

    fun onHostDenied() {
        codeDispatcher.denyHost()
    }

    fun onJoinerConfirmed() {
        codeDispatcher.confirmJoiner()
    }

    fun onJoinerDenied() {
        codeDispatcher.denyJoiner()
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            _commands.send(Command.SetPairingResult(SyncPairingResult.Failure))
            _commands.send(Command.Close)
        }
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch {
            val code = viewState.value.bitmap?.url ?: return@launch
            val isNotificationShown = clipboard.copyToClipboard(code, isSensitive = true)
            if (!isNotificationShown) {
                _commands.send(Command.ShowMessage(R.string.sync_simplified_qr_code_copied_message))
            }
            pixels.fireSyncSetupCodeCopiedToClipboard(syncType)
        }
    }

    fun onShareCodeClicked() {
        viewModelScope.launch {
            val code = viewState.value.bitmap?.url ?: return@launch
            _commands.send(Command.ShareCode(code))
        }
    }

    private suspend fun startV1Presentation() {
        if (isSignedIn) {
            startV1ExchangePresentation()
        } else {
            startV1ConnectPresentation()
        }
    }

    private suspend fun startV1ConnectPresentation() = coroutineScope {
        val qrCodeResult = accountRepository.getConnectQR()
            .onSuccess { showQrCode(it.qrCode) }
            .onFailure { failure ->
                val content = V1PairingErrorContent(R.string.sync_connect_generic_error, failure.reason)
                _commands.send(Command.ShowV1Error(content))
            }

        var isPolling = qrCodeResult is Result.Success
        while (isPolling) {
            delay(POLLING_INTERVAL_CONNECT_FLOW)
            accountRepository.pollConnectionKeys()
                .onSuccess { isSynced ->
                    if (isSynced) {
                        pixels.fireSignupConnectPixel(launchSource)
                        pixels.fireSyncSetupFinishedSuccessfully(syncType)

                        _commands.send(Command.SetPairingResult(pairingResult()))
                        _commands.send(Command.Close)
                        isPolling = false
                    }
                }
                .onFailure { failure ->
                    _commands.send(Command.ShowV1Error(failure.toV1PairingError()))
                    isPolling = false
                }
        }
    }

    private suspend fun startV1ExchangePresentation() {
        if (!syncFeature.exchangeKeysToSyncWithAnotherDevice().isEnabled()) {
            accountRepository.getRecoveryCode()
                .onSuccess { showQrCode(it.qrCode) }
                .onFailure { failure ->
                    val content = V1PairingErrorContent(R.string.sync_connect_generic_error, failure.reason)
                    _commands.send(Command.ShowV1Error(content))
                }
            return
        }

        val invitationResult = accountRepository.generateExchangeInvitationCode()
            .onSuccess { showQrCode(it.qrCode) }
            .onFailure { failure ->
                val content = V1PairingErrorContent(R.string.sync_connect_generic_error, failure.reason)
                _commands.send(Command.ShowV1Error(content))
            }

        var isPolling = invitationResult is Result.Success
        while (isPolling) {
            delay(POLLING_INTERVAL_EXCHANGE_FLOW)
            accountRepository.pollSecondDeviceExchangeAcknowledgement()
                .onSuccess { isAcknowledged ->
                    if (isAcknowledged) {
                        // The peer joined this account; this device did not log in, so no login pixel.
                        pixels.fireSyncSetupFinishedSuccessfully(syncType)

                        _commands.send(Command.SetPairingResult(pairingResult()))
                        _commands.send(Command.Close)
                        isPolling = false
                    }
                }
                .onFailure { failure ->
                    _commands.send(Command.ShowV1Error(failure.toV1PairingError()))
                    isPolling = false
                }
        }
    }

    private suspend fun startV2Presentation() {
        codeDispatcher.presentV2().collect { outcome ->
            pixels.fireSetupFailed(syncType, outcome)
            pixels.fireSetupCancelledIfDenied(syncType, outcome)

            when (outcome) {
                is DispatchOutcome.LinkingCodeReady -> {
                    showQrCode(outcome.linkingCode)
                }

                is DispatchOutcome.HostConfirmationRequested -> {
                    _commands.send(Command.AskHostConfirmation(outcome.peerName, outcome.peerKind))
                }

                is DispatchOutcome.JoinerConfirmationRequested -> {
                    _commands.send(Command.AskJoinerConfirmation(outcome.peerName, outcome.peerKind))
                }

                is DispatchOutcome.LoggedIn -> {
                    pixels.fireLoginPixel()
                    pixels.fireSyncSetupFinishedSuccessfully(syncType, outcome.path, outcome.myRole, outcome.peerKind)
                    _commands.send(Command.SetPairingResult(pairingResult()))
                    _commands.send(Command.Close)
                }

                is DispatchOutcome.AlreadyConnected -> {
                    _commands.send(Command.ShowV2Error(v2AlreadyPairedError))
                }

                is DispatchOutcome.UpgradeRequired -> {
                    _commands.send(Command.ShowV2Error(v2UpgradeRequiredError))
                }

                is DispatchOutcome.Failed -> {
                    _commands.send(Command.ShowV2Error(outcome.code.toV2PairingError()))
                }
            }
        }
    }

    private suspend fun showQrCode(url: String) {
        val bitmap = withContext(dispatchers.io()) {
            qrEncoder.encodeAsBitmap(url, R.dimen.simplifiedSyncQrSize, R.dimen.simplifiedSyncQrSize)
        }
        val displayCode = SyncBarcodeUrl.parseUrl(url)?.webSafeB64EncodedCode ?: url
        val bitmapWithCode = BitmapWithCode(bitmap, url, displayCode)
        _viewState.update { it.copy(bitmap = bitmapWithCode) }
    }

    private suspend fun pairingResult(): SyncPairingResult = withContext(dispatchers.io()) {
        accountRepository
            .getThisConnectedDevice()
            ?.let(ParcelableDevice::fromConnectedDevice)
            ?.let { device -> SyncPairingResult.Success(device, syncEntryPoint) }
            ?: SyncPairingResult.Failure
    }

    private suspend fun protocolVersion(): ProtocolVersion = withContext(dispatchers.io()) {
        if (syncFeature.canUseV2ConnectFlow().isEnabled() && syncFeature.canShowV2ConnectCode().isEnabled()) {
            ProtocolVersion.V2
        } else {
            ProtocolVersion.V1
        }
    }

    data class ViewState(
        val bitmap: BitmapWithCode? = null,
    )

    data class BitmapWithCode(
        val bitmap: Bitmap,
        val url: String,
        val displayCode: String,
    )

    internal sealed interface Command {
        data class AskJoinerConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : Command

        data class AskHostConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : Command

        data class ShowV1Error(
            val content: V1PairingErrorContent,
        ) : Command

        data class ShowV2Error(
            val content: V2PairingErrorContent,
        ) : Command

        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command

        data class ShareCode(
            val code: String,
        ) : Command

        data class SetPairingResult(
            val result: SyncPairingResult,
        ) : Command

        data object Close : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(
            syncEntryPoint: SyncEntryPoint,
            launchSource: String?,
        ): DisplayQrCodeViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val syncEntryPoint: SyncEntryPoint,
            private val launchSource: String?,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(syncEntryPoint, launchSource) as T
            }
        }
    }

    companion object {
        private val POLLING_INTERVAL_CONNECT_FLOW = 5.seconds
        private val POLLING_INTERVAL_EXCHANGE_FLOW = 2.seconds
    }
}
