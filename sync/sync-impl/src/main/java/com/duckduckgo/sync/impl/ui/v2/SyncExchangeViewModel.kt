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
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.fireSetupCancelledIfDenied
import com.duckduckgo.sync.impl.pixels.fireSetupFailed
import com.duckduckgo.sync.impl.ui.V2PairingErrorContent
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion
import com.duckduckgo.sync.impl.ui.toV2PairingError
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncExchangeViewModel @Inject constructor(
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val pixels: SyncPixels,
    private val syncFeature: SyncFeature,
    private val qrEncoder: QREncoder,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    internal val commands = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch(dispatchers.io()) {
            when (protocolVersion()) {
                ProtocolVersion.V1 -> startV1Presentation()
                ProtocolVersion.V2 -> startV2Presentation()
            }
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            _commands.send(Command.SetFailureResult)
            _commands.send(Command.Close)
        }
    }

    private suspend fun startV1Presentation() {
        accountRepository.getConnectQR()
            .onSuccess { showQrCode(it.qrCode) }
            .onFailure { _commands.send(Command.ShowError(R.string.sync_connect_generic_error, it.reason)) }
    }

    private suspend fun startV2Presentation() {
        codeDispatcher.presentV2().collect { outcome ->
            pixels.fireSetupFailed(SYNC_CONNECT, outcome)
            pixels.fireSetupCancelledIfDenied(SYNC_CONNECT, outcome)

            when (outcome) {
                is DispatchOutcome.LinkingCodeReady -> {
                    showQrCode(outcome.linkingCode)
                }

                is DispatchOutcome.HostConfirmationRequested -> {
                    logcat { "TODO" }
                }

                is DispatchOutcome.JoinerConfirmationRequested -> {
                    logcat { "TODO" }
                }

                is DispatchOutcome.LoggedIn -> {
                    logcat { "TODO" }
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
        data class ShowError(
            @StringRes val message: Int,
            val reason: String = "",
        ) : Command

        data class ShowV2Error(
            val content: V2PairingErrorContent,
        ) : Command

        data object SetFailureResult : Command

        data object Close : Command
    }
}
