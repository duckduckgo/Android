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
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.OriginalFlow
import com.duckduckgo.sync.impl.ui.V1PairingErrorContent
import com.duckduckgo.sync.impl.ui.V2PairingErrorContent
import com.duckduckgo.sync.impl.ui.toV1PairingError
import com.duckduckgo.sync.impl.ui.toV2PairingError
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class ExchangeSyncCodeViewModel @AssistedInject constructor(
    @Assisted private val syncUrl: String,
    @Assisted private val originalFlow: OriginalFlow,
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val syncFeature: SyncFeature,
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

    fun onUserAcceptedSwitchingAccount(encodedStringCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            accountRepository.logoutAndJoinNewAccount(encodedStringCode)
                .onSuccess {
                    animationCompletionSignal.await()
                    _commands.send(Command.SetPairingResult(pairingResult()))
                    _commands.send(Command.Close)
                }
                .onFailure { failure ->
                    _commands.send(Command.ShowV1Error(failure.toV1PairingError()))
                }
        }
    }

    fun onUserCancelledSwitchingAccount() {
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
                            pollForV1RecoveryKey()
                        }

                        else -> {
                            animationCompletionSignal.await()
                            _commands.send(Command.SetPairingResult(pairingResult()))
                            _commands.send(Command.Close)
                        }
                    }
                }
                .onFailure { failure ->
                    emitV1Error(failure)
                }
        }
    }

    private suspend fun pollForV1RecoveryKey() {
        var isPolling = true
        while (isPolling) {
            delay(POLLING_INTERVAL_EXCHANGE_FLOW)
            withContext(dispatchers.io()) { accountRepository.pollForRecoveryCodeAndLogin() }
                .onSuccess { result ->
                    when (result) {
                        is AccountSwitchingRequired -> {
                            _commands.send(Command.AskSwitchAccount(result.recoveryCode))
                            isPolling = false
                        }

                        is LoggedIn -> {
                            animationCompletionSignal.await()
                            _commands.send(Command.SetPairingResult(pairingResult()))
                            _commands.send(Command.Close)
                            isPolling = false
                        }

                        is Pending -> Unit
                    }
                }
                .onFailure { failure ->
                    emitV1Error(failure)
                    isPolling = false
                }
        }
    }

    private suspend fun emitV1Error(failure: Error) {
        if (failure.code == ALREADY_SIGNED_IN.code && syncFeature.seamlessAccountSwitching().isEnabled()) {
            _commands.send(Command.AskSwitchAccount(syncUrl))
        } else {
            _commands.send(Command.ShowV1Error(failure.toV1PairingError()))
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

    internal data class ViewState(
        val isLoggedIn: Boolean = false,
    )

    private suspend fun pairingResult(): SyncPairingResult = withContext(dispatchers.io()) {
        accountRepository
            .getThisConnectedDevice()
            ?.let(ParcelableDevice::fromConnectedDevice)
            ?.let { device -> SyncPairingResult.Success(device, originalFlow) }
            ?: SyncPairingResult.Failure
    }

    internal sealed interface Command {
        data class AskJoinerConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : Command

        data class AskHostConfirmation(
            val peerName: String?,
            val peerKind: PeerKind? = null,
        ) : Command

        data class AskSwitchAccount(
            val encodedStringCode: String,
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

    @AssistedFactory
    interface Factory {
        fun create(
            syncUrl: String,
            originalFlow: OriginalFlow,
        ): ExchangeSyncCodeViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val syncUrl: String,
            private val originalFlow: OriginalFlow,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(syncUrl, originalFlow) as T
            }
        }
    }

    companion object {
        private val POLLING_INTERVAL_EXCHANGE_FLOW = 2.seconds
    }
}
