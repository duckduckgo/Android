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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.ui.V1PairingErrorContent
import com.duckduckgo.sync.impl.ui.V2PairingErrorContent
import com.duckduckgo.sync.impl.ui.toV1PairingError
import com.duckduckgo.sync.impl.ui.toV2PairingError
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.PairingMethod
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.Path
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

class ExchangeSyncCodeViewModel @AssistedInject constructor(
    @Assisted private val syncUrl: String,
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    internal val commands = _commands.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    internal val viewState = _viewState.asStateFlow()

    private val animationCompletionSignal = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch(dispatchers.io()) {
            when (val decision = codeDispatcher.route(syncUrl)) {
                is RouteDecision.Legacy -> handleV1CodeDecision(decision)
                is RouteDecision.V2InProgress -> handleV2CodeDecision(decision)
            }
        }
    }

    fun runAcknowledgementAnimation() {
        viewModelScope.launch {
            _commands.send(Command.RunAcknowledgmentAnimation)
        }
    }

    fun onHostConfirmed() {
        codeDispatcher.confirmHost()
        viewModelScope.launch {
            _commands.send(Command.ShowPairingAcknowledgement)
        }
    }

    fun onHostDenied() {
        codeDispatcher.denyHost()
    }

    fun onJoinerConfirmed() {
        codeDispatcher.confirmJoiner()
        viewModelScope.launch {
            _commands.send(Command.ShowPairingAcknowledgement)
        }
    }

    fun onJoinerDenied() {
        codeDispatcher.denyJoiner()
    }

    fun onAnimationComplete() {
        animationCompletionSignal.complete(Unit)
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            _commands.send(Command.SetPairingResult(SyncPairingResult.Failure))
            _commands.send(Command.Close)
        }
    }

    private suspend fun handleV1CodeDecision(decision: RouteDecision.Legacy) {
        _commands.send(Command.RunAcknowledgmentAnimation)
        val authCode = decision.authCode
        withContext(dispatchers.io()) {
            accountRepository.processCode(authCode)
                .onSuccess {
                    when (authCode) {
                        is Exchange -> {
                            logcat { "TODO: Poll for recovery key" }
                        }

                        else -> {
                            val path = if (authCode is SyncAuthCode.Recovery) {
                                Path.Recovery
                            } else {
                                Path.Pairing(role = null, method = PairingMethod.ScannedCode)
                            }
                            animationCompletionSignal.await()
                            _commands.send(Command.SetPairingResult(pairingResult(path)))
                            _commands.send(Command.Close)
                        }
                    }
                }
                .onFailure { failure ->
                    _commands.send(Command.ShowV1Error(failure.toV1PairingError()))
                }
        }
    }

    private suspend fun handleV2CodeDecision(decision: RouteDecision.V2InProgress) {
        decision.outcomes.collect(::processV2Outcome)
    }

    private suspend fun processV2Outcome(outcome: DispatchOutcome) {
        when (outcome) {
            // No-op. QR code is not show while sync is in progress in the simplified sync.
            is DispatchOutcome.LinkingCodeReady -> Unit

            is DispatchOutcome.HostConfirmationRequested -> {
                _commands.send(Command.AskHostConfirmation(outcome.peerName, outcome.peerKind))
            }

            is DispatchOutcome.JoinerConfirmationRequested -> {
                _commands.send(Command.AskJoinerConfirmation(outcome.peerName, outcome.peerKind))
            }

            is DispatchOutcome.LoggedIn -> {
                _viewState.update { it.copy(isLoggedIn = true) }
                if (outcome.path == SetupPath.RECOVERY) {
                    _commands.send(Command.RunAcknowledgmentAnimation)
                }
                animationCompletionSignal.await()

                val path = outcome.toPairingPath(PairingMethod.ScannedCode)
                _commands.send(Command.SetPairingResult(pairingResult(path)))
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

    internal data class ViewState(
        val isLoggedIn: Boolean = false,
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

        data object ShowPairingAcknowledgement : Command

        data object RunAcknowledgmentAnimation : Command

        data class ShowV1Error(
            val content: V1PairingErrorContent,
        ) : Command

        data class ShowV2Error(
            val content: V2PairingErrorContent,
        ) : Command

        data class SetPairingResult(
            val result: SyncPairingResult,
        ) : Command

        data object Close : Command
    }

    private suspend fun pairingResult(path: Path): SyncPairingResult = withContext(dispatchers.io()) {
        accountRepository
            .getThisConnectedDevice()
            ?.let(ParcelableDevice::fromConnectedDevice)
            ?.let { device -> SyncPairingResult.Success(device, path) }
            ?: SyncPairingResult.Failure
    }

    @AssistedFactory
    interface Factory {
        fun create(syncUrl: String): ExchangeSyncCodeViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val syncUrl: String,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(syncUrl) as T
            }
        }
    }
}
