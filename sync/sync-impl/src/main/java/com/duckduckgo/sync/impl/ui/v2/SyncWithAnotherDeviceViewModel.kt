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
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

class SyncWithAnotherDeviceViewModel @AssistedInject constructor(
    @Assisted private val syncUrl: String,
    private val accountRepository: SyncAccountRepository,
    private val codeDispatcher: SyncCodeDispatcher,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _commands = Channel<Command>(Channel.BUFFERED)
    internal val commands = _commands.receiveAsFlow()

    private val animationCompletionSignal = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch(dispatchers.io()) {
            when (val decision = codeDispatcher.route(syncUrl)) {
                is RouteDecision.Legacy -> handleV1CodeDecision(decision)
                is RouteDecision.V2InProgress -> handleV2CodeDecision(decision)
            }
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

    fun onPairingAcknowledged() {
        viewModelScope.launch {
            _commands.send(Command.RunAcknowledgmentAnimation)
        }
    }

    fun onAnimationComplete() {
        animationCompletionSignal.complete(Unit)
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            _commands.send(Command.SetFailureResult)
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
                            animationCompletionSignal.await()
                            _commands.send(Command.SetSuccessResult)
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
                animationCompletionSignal.await()
                _commands.send(Command.SetSuccessResult)
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

        data object SetFailureResult : Command

        data object SetSuccessResult : Command

        data object Close : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(syncUrl: String): SyncWithAnotherDeviceViewModel

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
