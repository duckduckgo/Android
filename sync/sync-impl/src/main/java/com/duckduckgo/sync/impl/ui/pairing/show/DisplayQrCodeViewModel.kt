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

package com.duckduckgo.sync.impl.ui.pairing.show

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
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.pixels.SwitchedAccountDetector
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_EXCHANGE
import com.duckduckgo.sync.impl.pixels.fireSetupCancelledIfDenied
import com.duckduckgo.sync.impl.pixels.fireSetupFailed
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.devices.ParcelableDevice
import com.duckduckgo.sync.impl.ui.pairing.ExchangeV2PairingErrorContent
import com.duckduckgo.sync.impl.ui.pairing.SyncPairingResult
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2UpgradeRequiredError
import com.duckduckgo.sync.impl.ui.pairing.toExchangeV2PairingError
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayQrCodeViewModel @AssistedInject constructor(
    @Assisted private val syncEntryPoint: SyncEntryPoint,
    @Assisted private val launchSource: String,
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val pixels: SyncPixels,
    private val qrEncoder: QREncoder,
    private val clipboard: ClipboardInteractor,
    private val switchedAccountDetector: SwitchedAccountDetector,
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
            switchedAccountDetector.onPairingStarted()
            pixels.fireSyncBarcodeScreenShown(syncType)
            startV2Presentation()
        }
    }

    fun onHostConfirmed() {
        _viewState.update { it.copy(dialog = DialogType.Connecting) }
        codeDispatcher.confirmHost()
    }

    fun onHostDenied() {
        _viewState.update { it.copy(dialog = null) }
        codeDispatcher.denyHost()
    }

    fun onJoinerConfirmed() {
        _viewState.update { it.copy(dialog = DialogType.Connecting) }
        codeDispatcher.confirmJoiner()
    }

    fun onJoinerDenied() {
        _viewState.update { it.copy(dialog = null) }
        codeDispatcher.denyJoiner()
    }

    fun onErrorDialogDismissed() {
        closeWithoutPairing()
    }

    fun onConnectingCancelled() {
        closeWithoutPairing()
    }

    fun onCheckOtherDeviceCancelled() {
        closeWithoutPairing()
    }

    private fun closeWithoutPairing() {
        _viewState.update { it.copy(dialog = null) }
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

    private suspend fun startV2Presentation() {
        codeDispatcher.presentV2().collect { outcome ->
            pixels.fireSetupFailed(syncType, launchSource, outcome)
            pixels.fireSetupCancelledIfDenied(syncType, launchSource, outcome)

            when (outcome) {
                is DispatchOutcome.LinkingCodeReady -> {
                    showQrCode(outcome.linkingCode)
                }

                is DispatchOutcome.HostConfirmationRequested -> {
                    _viewState.update { it.copy(dialog = DialogType.HostConfirmation(outcome.peerName, outcome.peerKind)) }
                }

                is DispatchOutcome.JoinerConfirmationRequested -> {
                    _viewState.update { it.copy(dialog = DialogType.JoinerConfirmation(outcome.peerName, outcome.peerKind)) }
                }

                is DispatchOutcome.LoggedIn -> {
                    _viewState.update { it.copy(dialog = null) }
                    pixels.fireLoginPixel()
                    switchedAccountDetector.onPaired()
                    pixels.fireSyncSetupFinishedSuccessfully(syncType, launchSource, outcome.path, outcome.myRole, outcome.peerKind)
                    _commands.send(Command.SetPairingResult(pairingResult()))
                    _commands.send(Command.Close)
                }

                is DispatchOutcome.AlreadyConnected -> {
                    _viewState.update { it.copy(dialog = null) }
                    _commands.send(Command.ShowV2Error(exchangeV2AlreadyPairedError))
                }

                is DispatchOutcome.JoinOutcomeUnknown -> {
                    _viewState.update { it.copy(dialog = DialogType.CheckOtherDevice) }
                }

                is DispatchOutcome.UpgradeRequired -> {
                    _viewState.update { it.copy(dialog = null) }
                    _commands.send(Command.ShowV2Error(exchangeV2UpgradeRequiredError))
                }

                is DispatchOutcome.Failed -> {
                    _viewState.update { it.copy(dialog = null) }
                    _commands.send(Command.ShowV2Error(outcome.code.toExchangeV2PairingError()))
                }
            }
        }
    }

    private suspend fun showQrCode(url: String) {
        val bitmap = withContext(dispatchers.io()) {
            qrEncoder.encodeAsBitmap(url, R.dimen.qrBarcodeSize, R.dimen.qrBarcodeSize)
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

    data class ViewState(
        val bitmap: BitmapWithCode? = null,
        val dialog: DialogType? = null,
    )

    sealed interface DialogType {
        data class HostConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : DialogType

        data class JoinerConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : DialogType

        data object Connecting : DialogType

        data object CheckOtherDevice : DialogType
    }

    data class BitmapWithCode(
        val bitmap: Bitmap,
        val url: String,
        val displayCode: String,
    )

    internal sealed interface Command {
        data class ShowV2Error(
            val content: ExchangeV2PairingErrorContent,
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
            launchSource: String,
        ): DisplayQrCodeViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val syncEntryPoint: SyncEntryPoint,
            private val launchSource: String,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(syncEntryPoint, launchSource) as T
            }
        }
    }
}
