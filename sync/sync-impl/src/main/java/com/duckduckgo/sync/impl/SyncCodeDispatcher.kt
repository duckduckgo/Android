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
 * Routes a pasted/scanned sync code to either the v1 stack or the v2 exchange protocol, gated by
 * [SyncFeature.canUseV2ConnectFlow]. With the flag off, [route] behaves identically to
 * [SyncAccountRepository.parseSyncAuthCode]; v2 work is exposed via [RouteDecision.V2InProgress].
 */
interface SyncCodeDispatcher {

    /**
     * Inspects the pasted code and decides who handles it: [RouteDecision.Legacy] (caller processes
     * the [SyncAuthCode] as today) or [RouteDecision.V2InProgress] (dispatcher owns the work; caller
     * collects the returned Flow). See [DispatchOutcome] for the v2 emitted sequence.
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

    /**
     * Starts a v2 Presenter session. The returned Flow emits exactly one
     * [DispatchOutcome.LinkingCodeReady] (the URL to render as a QR), then the v2 emitted sequence
     * (see [DispatchOutcome]). Role election runs in the runner, so the Presenter may be elected
     * Host or Joiner. Cancelling the collecting coroutine cancels the underlying runner session.
     */
    fun presentV2(): Flow<DispatchOutcome>
}

sealed interface RouteDecision {
    /** Caller MUST process [authCode] as it would have today — no behaviour change from FF off. */
    data class Legacy(val authCode: SyncAuthCode) : RouteDecision

    /**
     * Dispatcher owns the work. [codeType] is the kind of v2 code that was recognized.
     * [outcomes] is a cold Flow that drives the v2 work when collected (see [DispatchOutcome] for the
     * emitted sequence). Cancelling the collector cancels the work.
     */
    data class V2InProgress(
        val codeType: SyncCodeType,
        val outcomes: Flow<DispatchOutcome>,
    ) : RouteDecision
}

enum class SyncCodeType { RECOVERY, LINKING }

/**
 * Outcomes emitted by a v2-owned dispatch, used by both [SyncCodeDispatcher.route] (Scanner) and
 * [SyncCodeDispatcher.presentV2] (Presenter). Emitted sequence per side:
 *
 * Scanner (via [RouteDecision.V2InProgress.outcomes]): zero or one [JoinerConfirmationRequested] /
 * [HostConfirmationRequested] (caller prompts and resumes via the confirm/deny calls), then exactly
 * one terminal ([LoggedIn] / [UpgradeRequired] / [Failed]).
 *
 * Presenter: exactly one [LinkingCodeReady], then zero or one [JoinerConfirmationRequested] /
 * [HostConfirmationRequested] (role is elected by the runner), then exactly one terminal
 * ([LoggedIn] / [AlreadyConnected] / [Failed]).
 *
 * v2 RecoveryCode flows (cid=ddg, cid=3party) have no confirmation phase — only a terminal outcome.
 *
 * v2 does not surface a v1-style `AskToSwitchAccount` prompt; the spec's Confirmations phase is the
 * consent step. On `ALREADY_SIGNED_IN` the dispatcher logs out and rejoins transparently, emitting
 * [LoggedIn] (or [Failed] if the switch fails).
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

    /**
     * Emitted once per [SyncCodeDispatcher.presentV2] session, before any confirmation or
     * terminal outcome. [linkingCode] is the URL the caller renders as a QR for the peer
     * to scan. Non-terminal — the Flow continues.
     */
    data class LinkingCodeReady(val linkingCode: String) : DispatchOutcome

    /** Terminal — login completed (recovery code applied; account state updated). */
    data object LoggedIn : DispatchOutcome

    /**
     * Terminal — peer and this device are already on the same account. Per spec §"Same-account
     * case" this is not a failure: show a "Connected" confirmation. No account state change.
     */
    data object AlreadyConnected : DispatchOutcome

    /** Terminal — the pasted code requires a protocol major version higher than this app supports. */
    data class UpgradeRequired(val codeMajor: Int) : DispatchOutcome

    /**
     * Terminal — transport error, missing credentials on this device, BE rejection, denial, etc.
     * [code] carries the originating [AccountErrorCodes] code (defaults to GENERIC_ERROR) so callers
     * can map specific failures (e.g. THIRD_PARTY_ALREADY_UPGRADED) to user-facing copy.
     */
    data class Failed(
        val reason: String,
        val code: Int = AccountErrorCodes.GENERIC_ERROR.code,
    ) : DispatchOutcome
}
