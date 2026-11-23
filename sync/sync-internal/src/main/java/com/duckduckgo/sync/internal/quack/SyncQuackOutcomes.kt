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

package com.duckduckgo.sync.internal.quack

import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Terminal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject

internal fun Result<*>.toOutcome(successMessage: String): Outcome = when (this) {
    is Result.Success -> Outcome.Ok(successMessage)
    is Result.Error -> Outcome.Error("Failed with code $code: $reason")
}

internal suspend fun Flow<DispatchOutcome>.pairAutoAccepting(
    dispatcher: SyncCodeDispatcher,
    terminal: Terminal,
    pairedMessage: String,
): Outcome {
    val terminalOutcome = onEach { outcome -> handleIntermediateOutcome(outcome, dispatcher, terminal) }
        .first { it.isTerminal() }
    return when (terminalOutcome) {
        is DispatchOutcome.LoggedIn -> Outcome.Ok(pairedMessage)
        is DispatchOutcome.AlreadyConnected -> Outcome.Ok("Already connected to the same account")
        is DispatchOutcome.UpgradeRequired -> Outcome.Error(
            "The code needs protocol v${terminalOutcome.codeMajor}, which this app doesn't support",
        )
        is DispatchOutcome.Failed -> Outcome.Error("Failed with code ${terminalOutcome.code}: ${terminalOutcome.reason}")
        else -> error("Unexpected terminal outcome $terminalOutcome")
    }
}

private suspend fun handleIntermediateOutcome(outcome: DispatchOutcome, dispatcher: SyncCodeDispatcher, terminal: Terminal) {
    when (outcome) {
        is DispatchOutcome.LinkingCodeReady -> terminal.send(
            "Pairing code: ${outcome.linkingCode}",
            json = JSONObject().put("pairingCode", outcome.linkingCode).toString(),
        )
        is DispatchOutcome.JoinerConfirmationRequested -> {
            terminal.send("Approving the join confirmation for ${outcome.peerName ?: "the other device"}")
            dispatcher.confirmJoiner()
        }
        is DispatchOutcome.HostConfirmationRequested -> {
            terminal.send("Approving the host confirmation for ${outcome.peerName ?: "the other device"}")
            dispatcher.confirmHost()
        }
        is DispatchOutcome.JoinOutcomeUnknown -> terminal.send("Recovery code sent, waiting for the other device to report")
        else -> Unit
    }
}

private fun DispatchOutcome.isTerminal() = when (this) {
    is DispatchOutcome.LoggedIn,
    is DispatchOutcome.AlreadyConnected,
    is DispatchOutcome.UpgradeRequired,
    is DispatchOutcome.Failed,
    -> true
    is DispatchOutcome.JoinerConfirmationRequested,
    is DispatchOutcome.HostConfirmationRequested,
    is DispatchOutcome.JoinOutcomeUnknown,
    is DispatchOutcome.LinkingCodeReady,
    -> false
}
