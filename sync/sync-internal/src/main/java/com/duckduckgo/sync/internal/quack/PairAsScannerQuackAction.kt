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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import io.mehow.quack.required
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesIntoSet(AppScope::class)
class PairAsScannerQuackAction @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatcher: SyncCodeDispatcher,
) : QuackAction {
    private val code = Param.string("code", "Pairing code").required()

    override val id = "sync.pair.scanner"
    override val description = "Pairs with another device by its code"
    override val params = listOf(code)

    override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        val pastedCode = args[code].trim()
        return when (val decision = dispatcher.route(pastedCode)) {
            is RouteDecision.Legacy -> pairLegacy(decision.authCode, pastedCode, terminal)
            is RouteDecision.V2InProgress -> pairV2(decision, terminal)
        }
    }

    private suspend fun pairV2(decision: RouteDecision.V2InProgress, terminal: Terminal): Outcome {
        return decision.outcomes.pairAutoAccepting(dispatcher, terminal, "Paired (${decision.codeType.name.lowercase()} code)")
    }

    private suspend fun pairLegacy(authCode: SyncAuthCode, pastedCode: String, terminal: Terminal): Outcome {
        return when (authCode) {
            is SyncAuthCode.Recovery -> recover(authCode, pastedCode, terminal)
            is SyncAuthCode.Connect -> syncAccountRepository.processCode(authCode).toOutcome("Paired (v1 connect code)")
            is SyncAuthCode.Exchange -> exchange(authCode, terminal)
            is SyncAuthCode.Unknown -> Outcome.Error("The code is not a sync code")
        }
    }

    private suspend fun recover(authCode: SyncAuthCode.Recovery, pastedCode: String, terminal: Terminal): Outcome {
        val result = syncAccountRepository.processCode(authCode)
        if (result is Result.Error && result.code == ALREADY_SIGNED_IN.code) {
            return switchAccount(pastedCode, terminal)
        }
        return result.toOutcome("Paired (v1 recovery code)")
    }

    private suspend fun exchange(authCode: SyncAuthCode.Exchange, terminal: Terminal): Outcome {
        val postResult = syncAccountRepository.processCode(authCode)
        if (postResult is Result.Error) return postResult.toOutcome("")
        terminal.send("Waiting for the other device to send its recovery code")
        while (true) {
            delay(EXCHANGE_POLLING_INTERVAL)
            when (val pollResult = syncAccountRepository.pollForRecoveryCodeAndLogin()) {
                is Result.Error -> return pollResult.toOutcome("")
                is Result.Success -> when (val exchangeResult = pollResult.data) {
                    is Pending -> Unit
                    is LoggedIn -> return Outcome.Ok("Paired (v1 exchange code)")
                    is AccountSwitchingRequired -> return switchAccount(exchangeResult.recoveryCode, terminal)
                }
            }
        }
    }

    private suspend fun switchAccount(recoveryCode: String, terminal: Terminal): Outcome {
        terminal.send("Approving the account switch")
        val deviceId = syncAccountRepository.getAccountInfo().deviceId
        val logoutResult = syncAccountRepository.logout(deviceId)
        if (logoutResult is Result.Error) return logoutResult.toOutcome("")
        val authCode = syncAccountRepository.parseSyncAuthCode(recoveryCode)
        return syncAccountRepository.processCode(authCode).toOutcome("Switched accounts and paired")
    }

    private companion object {
        val EXCHANGE_POLLING_INTERVAL = 2.seconds
    }
}
