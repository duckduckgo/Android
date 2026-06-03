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

import android.util.Base64
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2QrCode
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSyncCodeDispatcher @Inject constructor(
    private val syncFeature: SyncFeature,
    private val syncAccountRepository: SyncAccountRepository,
    private val qrCode: ExchangeV2QrCode,
    private val runner: ExchangeV2Runner,
) : SyncCodeDispatcher {

    override fun confirmJoiner() {
        logcat { "$TAG: user confirmed Joiner side" }
        runner.localTrigger(LocalTrigger.UserConfirmedJoiner)
    }

    override fun denyJoiner() {
        logcat { "$TAG: user denied Joiner side" }
        runner.localTrigger(LocalTrigger.UserDeniedJoiner)
    }

    override fun confirmHost() {
        logcat { "$TAG: user confirmed Host side" }
        runner.localTrigger(LocalTrigger.UserConfirmedHost)
    }

    override fun denyHost() {
        logcat { "$TAG: user denied Host side" }
        runner.localTrigger(LocalTrigger.UserDeniedHost)
    }

    /**
     * Drive a v2 Presenter session. Mirrors [driveV2Linking] but for the Presenter side:
     * starts the runner via [ExchangeV2Runner.startPresent], scopes to events emitted after
     * session start (defensive against the runner's SharedFlow replay cache), and maps each
     * event to a [DispatchOutcome] via [mapV2PresentEventToOutcome].
     *
     * Today's callers include both the signed-in `SyncWithAnotherActivityViewModel` and the
     * signed-out `SyncConnectViewModel` (per M1.5, Asana subtask `1215246284113165`). Role
     * election in the runner can elect this device as Host (signed-in surface, or signed-out
     * against a signed-out peer with account-creation-on-demand at Host.Sending — see
     * `1215168582640073`) or as Joiner (signed-out surface against a signed-in or 3party peer).
     * All terminal mappings are first-class; see `mapV2PresentEventToOutcome` for the table.
     */
    override fun presentV2(): Flow<DispatchOutcome> = flow {
        val sessionStartMs = System.currentTimeMillis()
        logcat { "$TAG: V2 Presenter starting runner.startPresent (scoped to events since $sessionStartMs)" }
        runner.startPresent()
        var ownChannelId: String? = null
        emitAll(
            runner.eventsSince(sessionStartMs)
                .transformWhile { event ->
                    // Latch our session's channelId on the first SessionStarted. If a later
                    // SessionStarted arrives with a different channelId, the shared runner has
                    // been preempted by another caller (route()/startScan from EnterCode, for
                    // example). Terminate silently without emitting any DispatchOutcome — the
                    // new caller's Flow now owns the runner. Defence-in-depth against the
                    // duplicate-login race; the activity-side lifecycle scope normally prevents
                    // this from happening at all, but this catches the transition window.
                    if (event is ExchangeV2Event.SessionStarted) {
                        if (ownChannelId == null) {
                            ownChannelId = event.ownChannelId
                        } else if (event.ownChannelId != ownChannelId) {
                            logcat { "$TAG: V2 Presenter flow preempted (own=$ownChannelId, new=${event.ownChannelId}); terminating silently" }
                            return@transformWhile false
                        }
                    }
                    val outcome = mapV2PresentEventToOutcome(event) ?: return@transformWhile true
                    emit(outcome)
                    !outcome.isTerminal()
                },
        )
        logcat { "$TAG: V2 Presenter flow completed" }
    }.onCompletion { cause ->
        if (cause != null) {
            // Flow cancelled or threw before reaching a terminal SM state — tear down the
            // runner session so the channel DELETE fires (best-effort).
            // Spec: Unified Algorithm §Aborting; Track B subtask 1215139308232508.
            logcat { "$TAG: V2 Presenter flow completed with cause=${cause::class.simpleName}; cancelling runner" }
            runner.cancel()
        }
    }

    /**
     * Translate one runner event into a [DispatchOutcome] for the v2 Presenter flow,
     * or null for intermediate events the caller should ignore.
     *
     * The Joiner.* branches were defensive stubs in M1 (the signed-in surface always elects
     * Host via role-election rule 1: account-beats-no-account). M1.5 wires `SyncConnectViewModel`
     * (signed-out surface) to `presentV2`, so a signed-out Presenter facing a signed-in peer
     * is now elected Joiner — these branches are first-class. Mirrors mapV2LinkingEventToOutcome
     * behaviour (login on Joiner.Done; preserve abort reason).
     *
     * See Asana subtask `1215246284113165` for the M1.5 wire-up and `1215168582640073` for the
     * runner-side account-provisioning at Host.Sending (used by signed-out Presenter ↔ signed-out
     * peer scenarios where this device wins Presenter-beats-Scanner).
     */
    private fun mapV2PresentEventToOutcome(event: ExchangeV2Event): DispatchOutcome? = when (event) {
        is ExchangeV2Event.SessionStarted -> event.linkingCode?.let { DispatchOutcome.LinkingCodeReady(it) }
        is ExchangeV2Event.Transition -> when (event.to) {
            ExchangeV2State.Joiner.Confirming ->
                DispatchOutcome.JoinerConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Confirming ->
                DispatchOutcome.HostConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Done -> DispatchOutcome.LoggedIn
            ExchangeV2State.Host.Aborted -> when (event.localTrigger) {
                LocalTrigger.UserDeniedHost -> DispatchOutcome.Failed("user_denied")
                LocalTrigger.HostUnavailable -> DispatchOutcome.Failed("host_unavailable")
                else -> DispatchOutcome.Failed("host_aborted")
            }
            ExchangeV2State.SameAccountAbort -> DispatchOutcome.AlreadyConnected
            ExchangeV2State.Joiner.Done -> {
                val received = (event.trigger as? ExchangeV2Message.RecoveryCodeResponse)?.recoveryCode
                if (received.isNullOrBlank()) {
                    DispatchOutcome.Failed("Pairing completed without a recovery code")
                } else {
                    loginWithV2RecoveryCode(received)
                }
            }
            ExchangeV2State.Joiner.AbortedByHost -> {
                val msgType = event.trigger?.messageType ?: "abort"
                DispatchOutcome.Failed("Pairing aborted by peer ($msgType)")
            }
            ExchangeV2State.Joiner.AbortedLocal -> DispatchOutcome.Failed("Pairing cancelled on this device")
            ExchangeV2State.Aborted -> DispatchOutcome.Failed("negotiation_aborted")
            else -> null
        }
        is ExchangeV2Event.SessionError -> DispatchOutcome.Failed(event.message)
        else -> null
    }

    override fun route(pastedCode: String): RouteDecision {
        val v2Enabled = syncFeature.canUseV2ConnectFlow().isEnabled()
        logcat { "$TAG: route() called, canUseV2ConnectFlow=$v2Enabled" }
        if (!v2Enabled) {
            // Flag off → never look at v2 shapes. Behaviour is byte-identical to direct
            // parseSyncAuthCode + the caller's existing handling.
            return legacy(pastedCode, reason = "FF off")
        }
        return classifyV2(pastedCode)
    }

    private fun classifyV2(pastedCode: String): RouteDecision {
        val parsed = qrCode.parse(pastedCode)
        logcat { "$TAG: v2 parse=${parsed::class.simpleName}" }
        return when (parsed) {
            is ExchangeV2CodeParseResult.LinkingV2 -> {
                logcat { "$TAG: routing → V2 LinkingV2 (runner.startScan)" }
                RouteDecision.V2InProgress(driveV2Linking(pastedCode))
            }
            is ExchangeV2CodeParseResult.RecoveryCode -> {
                val cid = parsed.rawJson.optString("cid", "ddg")
                logcat { "$TAG: routing → V2 RecoveryCode (cid=$cid)" }
                RouteDecision.V2InProgress(driveV2Recovery(parsed, cid))
            }
            ExchangeV2CodeParseResult.LinkingV1,
            ExchangeV2CodeParseResult.Unknown,
            -> legacy(pastedCode, reason = "v2 parser=${parsed::class.simpleName}, falling through to legacy")
        }
    }

    private fun legacy(pastedCode: String, reason: String): RouteDecision.Legacy {
        val authCode = syncAccountRepository.parseSyncAuthCode(pastedCode)
        logcat { "$TAG: routing → legacy v1 ($reason); parseSyncAuthCode=${authCode::class.simpleName}" }
        return RouteDecision.Legacy(authCode)
    }

    /**
     * V2 recovery code (the new `{recovery:{user_id, secret, cid, v}}` shape). cid=ddg drives
     * the existing v1 Recovery login (same fields, just renamed). cid=3party drives the
     * 3party-join upgrade flow on [SyncAccountRepository]. Anything else → error.
     */
    private fun driveV2Recovery(parsed: ExchangeV2CodeParseResult.RecoveryCode, cid: String): Flow<DispatchOutcome> = flow {
        when (cid) {
            CID_DDG -> {
                val userId = parsed.rawJson.optString("user_id").takeIf { it.isNotEmpty() }
                val secret = parsed.rawJson.optString("secret").takeIf { it.isNotEmpty() }
                if (userId == null || secret == null) {
                    logcat { "$TAG: v2 recovery (ddg) missing user_id or secret" }
                    emit(DispatchOutcome.Failed("v2 recovery code missing required fields"))
                    return@flow
                }
                val authCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = secret, userId = userId))
                logcat { "$TAG: v2 recovery (ddg) → processCode(Recovery)" }
                emit(
                    syncAccountRepository.processCode(authCode, existingDeviceId = null).toOutcome(
                        label = "v2 recovery (ddg)",
                        codeForAccountSwitch = encodeV1RecoveryCodeAsB64(userId = userId, secret = secret),
                    ),
                )
            }
            CID_3PARTY -> {
                // joinAccountFromThirdPartyRecoveryCode takes the bare b64 code; re-encode the
                // inner recovery JSON so we don't need to crack the original URL fragment.
                val rewrapped = JSONObject().put("recovery", parsed.rawJson).toString()
                val b64 = Base64.encodeToString(
                    rewrapped.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                )
                logcat { "$TAG: v2 recovery (3party) → joinAccountFromThirdPartyRecoveryCode" }
                emit(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(b64).toOutcome(label = "v2 recovery (3party)"))
            }
            else -> {
                logcat { "$TAG: v2 recovery: unknown cid='$cid'" }
                emit(DispatchOutcome.Failed("Unknown v2 credential type: $cid"))
            }
        }
    }

    /**
     * V2 LinkingV2 (`code2=…`) scanner side. Kicks off [ExchangeV2Runner.startScan] and observes
     * its event stream — scoped via [ExchangeV2Runner.eventsSince] to ignore stale replayed
     * events from prior sessions — emitting outcomes as the SM progresses:
     *
     *  - [DispatchOutcome.JoinerConfirmationRequested] when the SM reaches Joiner.Confirming
     *    (caller must show a prompt and call [confirmJoiner] / [denyJoiner])
     *  - [DispatchOutcome.HostConfirmationRequested] when the SM reaches Host.Confirming
     *    (caller must show a prompt and call [confirmHost] / [denyHost])
     *  - A single terminal outcome ([LoggedIn] / [Failed] / etc.), after which the Flow completes
     *
     * Intermediate events (non-terminal transitions, MessageSent, etc.) are filtered via
     * [mapV2LinkingEventToOutcome] returning null and don't trigger emissions.
     */
    private fun driveV2Linking(pastedCode: String): Flow<DispatchOutcome> = flow {
        val sessionStartMs = System.currentTimeMillis()
        logcat { "$TAG: V2 LinkingV2 starting runner.startScan (scoped to events since $sessionStartMs)" }
        runner.startScan(pastedCode)
        var ownChannelId: String? = null
        emitAll(
            runner.eventsSince(sessionStartMs)
                .transformWhile { event ->
                    // Same preemption guard as presentV2(). Defence-in-depth against another
                    // caller invoking startScan or startPresent on the shared runner mid-flow.
                    if (event is ExchangeV2Event.SessionStarted) {
                        if (ownChannelId == null) {
                            ownChannelId = event.ownChannelId
                        } else if (event.ownChannelId != ownChannelId) {
                            logcat { "$TAG: V2 LinkingV2 flow preempted (own=$ownChannelId, new=${event.ownChannelId}); terminating silently" }
                            return@transformWhile false
                        }
                    }
                    val outcome = mapV2LinkingEventToOutcome(event) ?: return@transformWhile true
                    emit(outcome)
                    !outcome.isTerminal()
                },
        )
        logcat { "$TAG: V2 LinkingV2 flow completed" }
    }.onCompletion { cause ->
        if (cause != null) {
            // Flow cancelled or threw before reaching a terminal SM state — tear down the
            // runner session so the channel DELETE fires (best-effort).
            // Spec: Unified Algorithm §Aborting; Track B subtask 1215139308232508.
            logcat { "$TAG: V2 LinkingV2 flow completed with cause=${cause::class.simpleName}; cancelling runner" }
            runner.cancel()
        }
    }

    private fun DispatchOutcome.isTerminal(): Boolean = when (this) {
        is DispatchOutcome.LinkingCodeReady,
        is DispatchOutcome.JoinerConfirmationRequested,
        is DispatchOutcome.HostConfirmationRequested,
        -> false
        is DispatchOutcome.LoggedIn,
        is DispatchOutcome.AlreadyConnected,
        is DispatchOutcome.UpgradeRequired,
        is DispatchOutcome.Failed,
        -> true
    }

    /**
     * Translate one runner event into a terminal [DispatchOutcome] for the v2 scanner flow,
     * or null for intermediate events the caller should ignore.
     */
    private fun mapV2LinkingEventToOutcome(event: ExchangeV2Event): DispatchOutcome? = when (event) {
        is ExchangeV2Event.Transition -> when (event.to) {
            ExchangeV2State.Joiner.Confirming ->
                DispatchOutcome.JoinerConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Confirming ->
                DispatchOutcome.HostConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Joiner.Done -> {
                val received = (event.trigger as? ExchangeV2Message.RecoveryCodeResponse)?.recoveryCode
                if (received.isNullOrBlank()) {
                    DispatchOutcome.Failed("Pairing completed without a recovery code")
                } else {
                    loginWithV2RecoveryCode(received)
                }
            }
            ExchangeV2State.Joiner.AbortedByHost -> {
                val msgType = event.trigger?.messageType ?: "abort"
                DispatchOutcome.Failed("Pairing aborted by peer ($msgType)")
            }
            ExchangeV2State.Joiner.AbortedLocal -> DispatchOutcome.Failed("Pairing cancelled on this device")
            ExchangeV2State.Host.Aborted -> DispatchOutcome.Failed("Pairing aborted")
            // Per spec §"Same-account case": NOT an abort. Surface as a friendly "Connected"
            // finish — both devices are already on the same account, nothing to do.
            ExchangeV2State.SameAccountAbort -> DispatchOutcome.AlreadyConnected
            // Scanner-side Host.Done means we were elected Host and have shared a recovery
            // code with the peer — successful pairing from this device's perspective.
            ExchangeV2State.Host.Done -> DispatchOutcome.LoggedIn
            ExchangeV2State.Aborted -> DispatchOutcome.Failed("negotiation_aborted")
            else -> null
        }
        is ExchangeV2Event.SessionError -> {
            val match = VERSION_TOO_NEW_REGEX.find(event.message)
            if (match != null) {
                DispatchOutcome.UpgradeRequired(codeMajor = match.groupValues[1].toIntOrNull() ?: -1)
            } else {
                DispatchOutcome.Failed(event.message)
            }
        }
        else -> null
    }

    /**
     * Apply a v2 recovery code received over the LinkingV2 channel. The received string is the
     * raw base64-encoded v2 recovery payload (`{recovery:{user_id, secret, cid, v}}`).
     *
     * Per spec §"Exchange Share Recovery Code → Joiner":
     *   - cid=ddg: log in directly (processCode with v1 Recovery shape, same primary_key+user_id fields)
     *   - cid=3party: upgrade the account (joinAccountFromThirdPartyRecoveryCode wraps the
     *     "Native joining a 3party account" subroutine — POST /access-credentials/ddg, re-encrypt
     *     keys, log in with ddg credentials)
     */
    private fun loginWithV2RecoveryCode(b64: String): DispatchOutcome {
        val parsed = decodeV2Recovery(b64) ?: return DispatchOutcome.Failed("Couldn't parse received recovery code as v2.0")
        return when (parsed.cid) {
            CID_DDG -> {
                val recovery = SyncAuthCode.Recovery(RecoveryCode(primaryKey = parsed.secret, userId = parsed.userId))
                syncAccountRepository.processCode(recovery, existingDeviceId = null)
                    .toOutcome(
                        label = "v2 LinkingV2 login (ddg)",
                        codeForAccountSwitch = encodeV1RecoveryCodeAsB64(userId = parsed.userId, secret = parsed.secret),
                    )
            }
            CID_3PARTY -> {
                logcat { "$TAG: v2 LinkingV2 received 3party code → joinAccountFromThirdPartyRecoveryCode" }
                syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(b64)
                    .toOutcome(label = "v2 LinkingV2 login (3party upgrade)")
            }
            else -> DispatchOutcome.Failed("Received unknown credential type '${parsed.cid}' over v2 linking")
        }
    }

    private data class ParsedV2Recovery(val userId: String, val secret: String, val cid: String, val v: String)

    private fun decodeV2Recovery(b64: String): ParsedV2Recovery? = runCatching {
        val decoded = runCatching { java.util.Base64.getUrlDecoder().decode(b64) }
            .recoverCatching { java.util.Base64.getDecoder().decode(b64) }
            .getOrThrow()
        val root = JSONObject(String(decoded, Charsets.UTF_8))
        val rec = root.getJSONObject("recovery")
        ParsedV2Recovery(
            userId = rec.getString("user_id"),
            secret = rec.getString("secret"),
            cid = rec.optString("cid", CID_DDG),
            v = rec.optString("v", "2.0"),
        )
    }.getOrNull()

    /**
     * Map a repo [Result] to a [DispatchOutcome].
     *
     * If [codeForAccountSwitch] is supplied AND the repo failed with [ALREADY_SIGNED_IN], the
     * dispatcher transparently calls [SyncAccountRepository.logoutAndJoinNewAccount] with the
     * supplied code and emits the result. No user prompt — per spec the Confirmations phase
     * (or the act of pasting a recovery code) is already the consent step, so the v1-style
     * `AskToSwitchAccount` dialog does not apply.
     *
     * [codeForAccountSwitch] must be in a shape that [SyncAccountRepository.logoutAndJoinNewAccount]
     * can re-parse via [SyncAccountRepository.parseSyncAuthCode] — that's v1-only, so v2
     * callers must convert to v1 b64 shape first (see [encodeV1RecoveryCodeAsB64]).
     */
    private fun Result<Boolean>.toOutcome(
        label: String,
        codeForAccountSwitch: String? = null,
    ): DispatchOutcome = when (this) {
        is Result.Success -> {
            logcat { "$TAG: $label → success" }
            DispatchOutcome.LoggedIn
        }
        is Result.Error -> {
            logcat { "$TAG: $label → error: $reason (code=$code)" }
            if (code == ALREADY_SIGNED_IN.code && codeForAccountSwitch != null) {
                logcat { "$TAG: $label → ALREADY_SIGNED_IN, performing transparent logout+rejoin" }
                when (val switched = syncAccountRepository.logoutAndJoinNewAccount(codeForAccountSwitch)) {
                    is Result.Success -> {
                        logcat { "$TAG: $label → logout+rejoin success" }
                        DispatchOutcome.LoggedIn
                    }
                    is Result.Error -> {
                        logcat { "$TAG: $label → logout+rejoin failed: ${switched.reason} (code=${switched.code})" }
                        DispatchOutcome.Failed(switched.reason, switched.code)
                    }
                }
            } else {
                DispatchOutcome.Failed(reason, code)
            }
        }
    }

    /**
     * Re-encode a v2 ddg recovery (userId + secret) as a v1-shape `{recovery:{primary_key,
     * user_id}}` base64-url string, so the existing [SyncAccountRepository.logoutAndJoinNewAccount]
     * (which uses v1-only [SyncAccountRepository.parseSyncAuthCode]) can consume it when the
     * user accepts the account-switch prompt.
     */
    private fun encodeV1RecoveryCodeAsB64(userId: String, secret: String): String {
        val v1Json = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("primary_key", secret)
                    put("user_id", userId)
                },
            )
        }.toString()
        return Base64.encodeToString(
            v1Json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    companion object {
        private const val TAG = "Sync-CodeDispatch"
        private const val CID_DDG = "ddg"
        private const val CID_3PARTY = "3party"

        // Matches the SessionError message format thrown when EnvelopeVersionTooNew bubbles up
        // from the runner's poll loop ("Peer requires protocol v3; please update this app").
        private val VERSION_TOO_NEW_REGEX = Regex("""protocol v(\d+)""")
    }
}
