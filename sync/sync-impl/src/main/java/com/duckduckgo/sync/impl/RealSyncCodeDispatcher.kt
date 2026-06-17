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
import com.duckduckgo.sync.impl.AccountErrorCodes.NEGOTIATION_ABORTED
import com.duckduckgo.sync.impl.AccountErrorCodes.NO_RECOVERY_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_UNAVAILABLE
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2QrCode
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
     * Drive a v2 Presenter session: start the runner via [ExchangeV2Runner.startPresent] and map
     * each event to a [DispatchOutcome] via [mapV2PresentEventToOutcome]. Role election may elect
     * this device as either Host or Joiner.
     */
    override fun presentV2(): Flow<DispatchOutcome> = flow {
        val sessionStartMs = System.currentTimeMillis()
        logcat { "$TAG: V2 Presenter starting runner.startPresent (scoped to events since $sessionStartMs)" }
        runner.startPresent()
        var ownChannelId: String? = null
        // Snapshot the peer kind while the session is live; the runner clears it on terminal teardown,
        // so by the time we map the terminal event runner.peerKind is already null.
        var peerKind: PeerKind? = null
        emitAll(
            runner.eventsSince(sessionStartMs)
                .transformWhile { event ->
                    // A later SessionStarted with a different channelId means another caller has
                    // preempted the shared runner; terminate silently so its Flow owns the runner.
                    if (event is ExchangeV2Event.SessionStarted) {
                        if (ownChannelId == null) {
                            ownChannelId = event.ownChannelId
                        } else if (event.ownChannelId != ownChannelId) {
                            logcat { "$TAG: V2 Presenter flow preempted (own=$ownChannelId, new=${event.ownChannelId}); terminating silently" }
                            return@transformWhile false
                        }
                    }
                    runner.peerKind.toPeerKind()?.let { peerKind = it }
                    val outcome = (mapV2PresentEventToOutcome(event, peerKind) ?: return@transformWhile true)
                        .withFailureContext(SetupPath.PAIRING, roleFromTerminalState(event), peerKind)
                    emit(outcome)
                    !outcome.isTerminal()
                },
        )
        logcat { "$TAG: V2 Presenter flow completed" }
    }.onCompletion { cause ->
        if (cause != null) {
            // Cancelled/threw before a terminal state — tear down the session so the channel
            // DELETE fires. Spec: Unified Algorithm §Aborting.
            logcat { "$TAG: V2 Presenter flow completed with cause=${cause::class.simpleName}; cancelling runner" }
            runner.cancel()
        }
    }

    /**
     * Translate one runner event into a [DispatchOutcome] for the v2 Presenter flow,
     * or null for intermediate events the caller should ignore. Both Host and Joiner roles are
     * reachable here; mirrors [mapV2LinkingEventToOutcome] (login on Joiner.Done, preserve abort reason).
     */
    private fun mapV2PresentEventToOutcome(event: ExchangeV2Event, peerKind: PeerKind?): DispatchOutcome? = when (event) {
        is ExchangeV2Event.SessionStarted -> event.linkingCode?.let { DispatchOutcome.LinkingCodeReady(it) }
        is ExchangeV2Event.Transition -> when (event.to) {
            ExchangeV2State.Joiner.Confirming ->
                DispatchOutcome.JoinerConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Confirming ->
                DispatchOutcome.HostConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Done -> DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST, peerKind)
            ExchangeV2State.Host.Aborted -> hostAbortedToOutcome(event.localTrigger)
            ExchangeV2State.SameAccountAbort -> DispatchOutcome.AlreadyConnected
            ExchangeV2State.Joiner.Done -> {
                val received = (event.trigger as? ExchangeV2Message.RecoveryCodeResponse)?.recoveryCode
                if (received.isNullOrBlank()) {
                    DispatchOutcome.Failed("Pairing completed without a recovery code", NO_RECOVERY_CODE.code)
                } else {
                    loginWithV2RecoveryCode(received, peerKind)
                }
            }
            ExchangeV2State.Joiner.AbortedByHost -> when (event.trigger) {
                is ExchangeV2Message.RecoveryCodeDenied ->
                    DispatchOutcome.Failed("Pairing declined by peer", PAIRING_REJECTED.code)
                is ExchangeV2Message.RecoveryCodeUnavailable ->
                    DispatchOutcome.Failed("Peer has no recovery code", PAIRING_UNAVAILABLE.code)
                else -> DispatchOutcome.Failed("Pairing aborted by peer", PAIRING_REJECTED.code)
            }
            ExchangeV2State.Joiner.AbortedLocal -> DispatchOutcome.Failed("Pairing cancelled on this device", PAIRING_CANCELLED.code)
            ExchangeV2State.Aborted -> DispatchOutcome.Failed("negotiation_aborted", NEGOTIATION_ABORTED.code)
            else -> null
        }
        is ExchangeV2Event.SessionError -> sessionErrorToOutcome(event.message)
        else -> null
    }

    override fun route(pastedCode: String): RouteDecision {
        val v2Enabled = syncFeature.canUseV2ConnectFlow().isEnabled()
        logcat { "$TAG: route() called, canUseV2ConnectFlow=$v2Enabled" }
        if (!v2Enabled) {
            // Flag off → never look at v2 shapes; defer entirely to the v1 path.
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
                RouteDecision.V2InProgress(SyncCodeType.LINKING, driveV2Linking(pastedCode))
            }
            is ExchangeV2CodeParseResult.RecoveryCode -> {
                val cid = parsed.rawJson.optString("cid", "ddg")
                logcat { "$TAG: routing → V2 RecoveryCode (cid=$cid)" }
                RouteDecision.V2InProgress(SyncCodeType.RECOVERY, driveV2Recovery(parsed, cid))
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
                val primaryKey = v2SecretToV1PrimaryKey(secret)
                if (primaryKey == null) {
                    logcat { "$TAG: v2 recovery (ddg) secret is not valid base64" }
                    emit(DispatchOutcome.Failed("v2 recovery code has a malformed secret"))
                    return@flow
                }
                val authCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = primaryKey, userId = userId))
                logcat { "$TAG: v2 recovery (ddg) → processCode(Recovery)" }
                emit(
                    syncAccountRepository.processCode(authCode, existingDeviceId = null).toOutcome(
                        label = "v2 recovery (ddg)",
                        path = SetupPath.RECOVERY,
                        codeForAccountSwitch = encodeV1RecoveryCodeAsB64(userId = userId, secret = primaryKey),
                    ),
                )
            }
            CID_3PARTY -> {
                // joinAccountFromThirdPartyRecoveryCode takes the bare b64 code; re-encode the
                // inner recovery JSON. Mint via Moshi (not org.json) to match the Moshi consumer
                // parseThirdPartyRecoveryCode and avoid non-canonical JSON escaping.
                val rewrapped = thirdPartyRecoveryCodeAdapter.toJson(
                    ThirdPartyRecoveryCodeWrapper(
                        recovery = ThirdPartyRecoveryCode(
                            userId = parsed.rawJson.optString("user_id"),
                            secret = parsed.rawJson.optString("secret"),
                            cid = parsed.rawJson.optString("cid", CID_3PARTY),
                            v = parsed.rawJson.optString("v", RECOVERY_CODE_V2),
                        ),
                    ),
                )
                val b64 = Base64.encodeToString(
                    rewrapped.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                )
                logcat { "$TAG: v2 recovery (3party) → joinAccountFromThirdPartyRecoveryCode" }
                emit(
                    syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(
                        b64,
                    ).toOutcome(label = "v2 recovery (3party)", path = SetupPath.RECOVERY),
                )
            }
            else -> {
                logcat { "$TAG: v2 recovery: unknown cid='$cid'" }
                emit(DispatchOutcome.Failed("Unknown v2 credential type: $cid"))
            }
        }
    }.map { it.withFailureContext(SetupPath.RECOVERY, myRole = null, peerKind = null) }

    /**
     * V2 LinkingV2 (`code2=…`) scanner side. Starts [ExchangeV2Runner.startScan] and maps its
     * events to outcomes via [mapV2LinkingEventToOutcome]: confirmation requests (caller shows a
     * prompt and calls [confirmJoiner]/[denyJoiner] or [confirmHost]/[denyHost]) then a single
     * terminal outcome that completes the Flow.
     */
    private fun driveV2Linking(pastedCode: String): Flow<DispatchOutcome> = flow {
        val sessionStartMs = System.currentTimeMillis()
        logcat { "$TAG: V2 LinkingV2 starting runner.startScan (scoped to events since $sessionStartMs)" }
        runner.startScan(pastedCode)
        var ownChannelId: String? = null
        // Snapshot the peer kind while the session is live (see presentV2): the runner clears it on
        // terminal teardown, before we map the terminal event.
        var peerKind: PeerKind? = null
        emitAll(
            runner.eventsSince(sessionStartMs)
                .transformWhile { event ->
                    // Same preemption guard as presentV2(): bail if another caller takes over the
                    // shared runner mid-flow.
                    if (event is ExchangeV2Event.SessionStarted) {
                        if (ownChannelId == null) {
                            ownChannelId = event.ownChannelId
                        } else if (event.ownChannelId != ownChannelId) {
                            logcat { "$TAG: V2 LinkingV2 flow preempted (own=$ownChannelId, new=${event.ownChannelId}); terminating silently" }
                            return@transformWhile false
                        }
                    }
                    runner.peerKind.toPeerKind()?.let { peerKind = it }
                    val outcome = (mapV2LinkingEventToOutcome(event, peerKind) ?: return@transformWhile true)
                        .withFailureContext(SetupPath.PAIRING, roleFromTerminalState(event), peerKind)
                    emit(outcome)
                    !outcome.isTerminal()
                },
        )
        logcat { "$TAG: V2 LinkingV2 flow completed" }
    }.onCompletion { cause ->
        if (cause != null) {
            // Cancelled/threw before a terminal state — tear down the session so the channel
            // DELETE fires. Spec: Unified Algorithm §Aborting.
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
    private fun mapV2LinkingEventToOutcome(event: ExchangeV2Event, peerKind: PeerKind?): DispatchOutcome? = when (event) {
        is ExchangeV2Event.Transition -> when (event.to) {
            ExchangeV2State.Joiner.Confirming ->
                DispatchOutcome.JoinerConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Host.Confirming ->
                DispatchOutcome.HostConfirmationRequested(peerName = runner.peerName)
            ExchangeV2State.Joiner.Done -> {
                val received = (event.trigger as? ExchangeV2Message.RecoveryCodeResponse)?.recoveryCode
                if (received.isNullOrBlank()) {
                    DispatchOutcome.Failed("Pairing completed without a recovery code", NO_RECOVERY_CODE.code)
                } else {
                    loginWithV2RecoveryCode(received, peerKind)
                }
            }
            ExchangeV2State.Joiner.AbortedByHost -> when (event.trigger) {
                is ExchangeV2Message.RecoveryCodeDenied ->
                    DispatchOutcome.Failed("Pairing declined by peer", PAIRING_REJECTED.code)
                is ExchangeV2Message.RecoveryCodeUnavailable ->
                    DispatchOutcome.Failed("Peer has no recovery code", PAIRING_UNAVAILABLE.code)
                else -> DispatchOutcome.Failed("Pairing aborted by peer", PAIRING_REJECTED.code)
            }
            ExchangeV2State.Joiner.AbortedLocal -> DispatchOutcome.Failed("Pairing cancelled on this device", PAIRING_CANCELLED.code)
            ExchangeV2State.Host.Aborted -> hostAbortedToOutcome(event.localTrigger)
            // Per spec §"Same-account case": not an abort; both devices share an account already.
            ExchangeV2State.SameAccountAbort -> DispatchOutcome.AlreadyConnected
            // Elected Host and shared a recovery code — success from this device's perspective.
            ExchangeV2State.Host.Done -> DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST, peerKind)
            ExchangeV2State.Aborted -> DispatchOutcome.Failed("negotiation_aborted", NEGOTIATION_ABORTED.code)
            else -> null
        }
        is ExchangeV2Event.SessionError -> sessionErrorToOutcome(event.message)
        else -> null
    }

    private fun sessionErrorToOutcome(message: String): DispatchOutcome {
        val match = VERSION_TOO_NEW_REGEX.find(message)
        return if (match != null) {
            DispatchOutcome.UpgradeRequired(codeMajor = match.groupValues[1].toIntOrNull() ?: -1)
        } else {
            DispatchOutcome.Failed(message, PAIRING_FAILED.code)
        }
    }

    private fun hostAbortedToOutcome(localTrigger: LocalTrigger?): DispatchOutcome = when (localTrigger) {
        LocalTrigger.UserDeniedHost -> DispatchOutcome.Failed("user_denied", PAIRING_CANCELLED.code)
        LocalTrigger.HostUnavailable -> DispatchOutcome.Failed("host_unavailable", PAIRING_UNAVAILABLE.code)
        else -> DispatchOutcome.Failed("host_aborted", NEGOTIATION_ABORTED.code)
    }

    /**
     * Apply a v2 recovery code received over the LinkingV2 channel (raw base64
     * `{recovery:{user_id, secret, cid, v}}`). Per spec §"Exchange Share Recovery Code → Joiner":
     * cid=ddg logs in directly; cid=3party upgrades the account via
     * [SyncAccountRepository.joinAccountFromThirdPartyRecoveryCode].
     */
    private fun loginWithV2RecoveryCode(b64: String, peerKind: PeerKind?): DispatchOutcome {
        val parsed = decodeV2Recovery(b64) ?: return DispatchOutcome.Failed("Couldn't parse received recovery code as v2.0")
        return when (parsed.cid) {
            CID_DDG -> {
                val primaryKey = v2SecretToV1PrimaryKey(parsed.secret)
                    ?: return DispatchOutcome.Failed("Received recovery code has a malformed secret")
                val recovery = SyncAuthCode.Recovery(RecoveryCode(primaryKey = primaryKey, userId = parsed.userId))
                logcat { "$TAG: v2 LinkingV2 received ddg code → $recovery" }
                syncAccountRepository.processCode(recovery, existingDeviceId = null)
                    .toOutcome(
                        label = "v2 LinkingV2 login (ddg)",
                        path = SetupPath.PAIRING,
                        myRole = SetupRole.JOINER,
                        peerKind = peerKind,
                        codeForAccountSwitch = encodeV1RecoveryCodeAsB64(userId = parsed.userId, secret = primaryKey),
                    )
            }
            CID_3PARTY -> {
                logcat { "$TAG: v2 LinkingV2 received 3party code → joinAccountFromThirdPartyRecoveryCode" }
                syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(b64)
                    .toOutcome(
                        label = "v2 LinkingV2 login (3party upgrade)",
                        path = SetupPath.PAIRING,
                        myRole = SetupRole.JOINER,
                        peerKind = peerKind,
                    )
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
     * v2 wire `secret` is base64url (spec 1214802412121967); re-encode to standard base64 for the
     * v1 native login, normalising the alphabet first to also tolerate an already-standard secret.
     * Returns null if the secret isn't valid base64.
     */
    private fun v2SecretToV1PrimaryKey(secret: String): String? = runCatching {
        val bytes = Base64.decode(secret.replace('-', '+').replace('_', '/'), Base64.NO_WRAP)
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    /**
     * Map a repo [Result] to a [DispatchOutcome].
     *
     * If [codeForAccountSwitch] is supplied and the repo failed with [ALREADY_SIGNED_IN], switch
     * accounts transparently via [SyncAccountRepository.logoutAndJoinNewAccount] with no prompt:
     * the Confirmations phase is already the consent step, so the v1 `AskToSwitchAccount` dialog
     * does not apply.
     *
     * [codeForAccountSwitch] must be a v1 b64 shape [SyncAccountRepository.parseSyncAuthCode] can
     * re-parse, so v2 callers convert first (see [encodeV1RecoveryCodeAsB64]).
     */
    /** Map the runner's raw peer credential id ("ddg"/"3party") to the telemetry [PeerKind], or null. */
    private fun String?.toPeerKind(): PeerKind? = when (this) {
        CID_DDG -> PeerKind.DDG
        CID_3PARTY -> PeerKind.THIRD_PARTY
        else -> null
    }

    /** Elected role implied by a terminal transition's target state, for "Setup failed" telemetry. */
    private fun roleFromTerminalState(event: ExchangeV2Event): SetupRole? =
        when ((event as? ExchangeV2Event.Transition)?.to) {
            is ExchangeV2State.Host -> SetupRole.HOST
            is ExchangeV2State.Joiner -> SetupRole.JOINER
            else -> null
        }

    /**
     * Attach best-effort failure telemetry ([path]/[myRole]/[peerKind]) to a terminal error outcome.
     * No-op for non-error outcomes, so a [DispatchOutcome.LoggedIn] keeps the context set at its source.
     */
    private fun DispatchOutcome.withFailureContext(
        path: SetupPath,
        myRole: SetupRole?,
        peerKind: PeerKind?,
    ): DispatchOutcome = when (this) {
        is DispatchOutcome.Failed -> copy(path = path, myRole = myRole, peerKind = peerKind)
        is DispatchOutcome.UpgradeRequired -> copy(path = path, myRole = myRole, peerKind = peerKind)
        else -> this
    }

    private fun Result<Boolean>.toOutcome(
        label: String,
        path: SetupPath,
        myRole: SetupRole? = null,
        peerKind: PeerKind? = null,
        codeForAccountSwitch: String? = null,
    ): DispatchOutcome = when (this) {
        is Result.Success -> {
            logcat { "$TAG: $label → success" }
            DispatchOutcome.LoggedIn(path, myRole, peerKind)
        }
        is Result.Error -> {
            logcat { "$TAG: $label → error: $reason (code=$code)" }
            if (code == ALREADY_SIGNED_IN.code && codeForAccountSwitch != null) {
                logcat { "$TAG: $label → ALREADY_SIGNED_IN, performing transparent logout+rejoin" }
                when (val switched = syncAccountRepository.logoutAndJoinNewAccount(codeForAccountSwitch)) {
                    is Result.Success -> {
                        logcat { "$TAG: $label → logout+rejoin success" }
                        DispatchOutcome.LoggedIn(path, myRole, peerKind)
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
     * user_id}}` base64-url string so [SyncAccountRepository.logoutAndJoinNewAccount] (v1-only
     * [SyncAccountRepository.parseSyncAuthCode]) can consume it on account switch.
     */
    private fun encodeV1RecoveryCodeAsB64(userId: String, secret: String): String {
        // Mint via Moshi (not org.json) and reuse the v1 LinkCode shape, keeping this byte-identical
        // to a genuine v1 code so the parseSyncAuthCode round-trip stays robust.
        val v1Json = v1RecoveryCodeAdapter.toJson(LinkCode(recovery = RecoveryCode(primaryKey = secret, userId = userId)))
        return Base64.encodeToString(
            v1Json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    private val v1RecoveryCodeAdapter by lazy {
        Moshi.Builder().build().adapter(LinkCode::class.java)
    }

    private val thirdPartyRecoveryCodeAdapter by lazy {
        Moshi.Builder().build().adapter(ThirdPartyRecoveryCodeWrapper::class.java)
    }

    companion object {
        private const val TAG = "Sync-CodeDispatch"
        private const val CID_DDG = "ddg"
        private const val CID_3PARTY = "3party"

        // Extracts the major version from the EnvelopeVersionTooNew SessionError message.
        private val VERSION_TOO_NEW_REGEX = Regex("""protocol v(\d+)""")
    }
}
