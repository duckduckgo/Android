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

package com.duckduckgo.sync.impl

import kotlinx.coroutines.flow.Flow

/**
 * Routes a pasted/scanned sync code to either the existing v1 stack or the new v2 exchange
 * protocol, based on the [SyncFeature.canUseV2ConnectFlow] flag. The router is designed so
 * that **with the flag off, behaviour is identical to direct calls to
 * [SyncAccountRepository.parseSyncAuthCode]** — same return value, same side effects (none),
 * same downstream processing. Callers keep their existing v1 handling intact and only need
 * to add a new branch for [RouteDecision.V2InProgress] when they want to opt into v2.
 *
 * Why this split: v2 codes need richer outcomes (e.g. an in-flight pairing session, an
 * upgrade prompt for v3 codes that arrive at a v2 client) that don't map onto the existing
 * [SyncAuthCode] type. v1 codes still flow through the well-trodden production paths
 * unchanged.
 */
interface SyncCodeDispatcher {

    /**
     * Inspect the pasted code and decide who handles it.
     *
     *  - [RouteDecision.Legacy]: caller processes the returned [SyncAuthCode] exactly as today
     *    ([SyncAccountRepository.processCode], etc.).
     *  - [RouteDecision.V2InProgress]: dispatcher owns the work end-to-end; caller collects the
     *    returned [Flow] which emits zero or one [DispatchOutcome.JoinerConfirmationRequested] /
     *    [DispatchOutcome.HostConfirmationRequested] prompt requests (which the VM should surface
     *    as a dialog), followed by exactly one terminal outcome.
     */
    fun route(pastedCode: String): RouteDecision

    /**
     * User confirmed the Joiner-side prompt ("Sync your data with …?"). Drives the SM out of
     * Joiner.Confirming. No-op if no session is in that state.
     */
    fun confirmJoiner()

    /**
     * User denied the Joiner-side prompt. Drives the SM to Joiner.AbortedLocal. No message
     * sent to peer (per spec). No-op if no session is in Joiner.Confirming.
     */
    fun denyJoiner()

    /**
     * User confirmed the Host-side prompt ("Allow … to join your sync & backup?"). Drives the
     * SM out of Host.Confirming into Host.Sending. No-op if no session is in that state.
     */
    fun confirmHost()

    /**
     * User denied the Host-side prompt. Drives the SM to Host.Aborted and sends
     * recovery_code_denied to the peer. No-op if no session is in Host.Confirming.
     */
    fun denyHost()
}

sealed interface RouteDecision {
    /** Caller MUST process [authCode] as it would have today — no behaviour change from FF off. */
    data class Legacy(val authCode: SyncAuthCode) : RouteDecision

    /**
     * Dispatcher has accepted ownership. [outcomes] is a cold Flow that, when collected,
     * drives the v2 work and emits **exactly one** [DispatchOutcome] before completing.
     * Cancelling the collecting coroutine cancels any in-flight v2 work the dispatcher started.
     */
    data class V2InProgress(val outcomes: Flow<DispatchOutcome>) : RouteDecision
}

/**
 * Outcomes emitted by a v2-owned dispatch. A single [RouteDecision.V2InProgress] flow emits
 * **zero or one** [JoinerConfirmationRequested] / [HostConfirmationRequested] (intermediate —
 * caller must show a prompt and call [SyncCodeDispatcher.confirmJoiner] / [denyJoiner] /
 * [confirmHost] / [denyHost] to resume the SM), followed by **exactly one** terminal
 * ([LoggedIn] / [UpgradeRequired] / [Failed]).
 *
 * For v2 RecoveryCode flows (cid=ddg, cid=3party) there's no confirmation phase — only a
 * single terminal outcome.
 *
 * Note: v2 does **not** surface a v1-style `AskToSwitchAccount` prompt. The spec's Confirmations
 * phase is the consent step ("Sync your data with [peer]?"); by the time login runs, the user
 * has already opted out of any existing account. When the BE returns `ALREADY_SIGNED_IN`, the
 * dispatcher performs the logout-and-rejoin transparently and emits [LoggedIn] (or [Failed] if
 * the switch itself fails).
 */
sealed interface DispatchOutcome {
    /**
     * SM reached Joiner.Confirming. Caller must prompt the user ("Sync your data with [peerName]?")
     * then call [SyncCodeDispatcher.confirmJoiner] or [SyncCodeDispatcher.denyJoiner] to resume.
     */
    data class JoinerConfirmationRequested(val peerName: String?) : DispatchOutcome

    /**
     * SM reached Host.Confirming. Caller must prompt the user ("Allow [peerName] to join your
     * sync & backup?") then call [SyncCodeDispatcher.confirmHost] or [SyncCodeDispatcher.denyHost].
     */
    data class HostConfirmationRequested(val peerName: String?) : DispatchOutcome

    /** Terminal — login completed (recovery code applied; account state updated). */
    data object LoggedIn : DispatchOutcome

    /**
     * Terminal — peer and this device are already on the same account. Per spec
     * §"Same-account case", this is **not a failure** — show a friendly "Connected"
     * confirmation and finish. No account state change happened or is needed.
     */
    data object AlreadyConnected : DispatchOutcome

    /** Terminal — the pasted code requires a protocol major version higher than this app supports. */
    data class UpgradeRequired(val codeMajor: Int) : DispatchOutcome

    /** Terminal — transport error, missing credentials on this device, BE rejection, denial, etc. */
    data class Failed(val reason: String) : DispatchOutcome
}
