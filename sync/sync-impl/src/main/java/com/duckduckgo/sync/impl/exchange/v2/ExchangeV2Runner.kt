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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.crypto.RsaKeyPair
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Host
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Joiner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.SameAccountAbort
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.ERROR
import logcat.logcat
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Drives a single Exchange V2 protocol session.
 *
 * Two entry points: [startPresent] generates a v2 linking code (the URL is surfaced via
 * [SessionStarted]); [startScan] parses a peer's code and joins their session. From there
 * the runner manages the wire I/O: bootstrap channel via PUT, send messages via POST, poll
 * own channel for incoming envelopes, decrypt, drive the SM, auto-elect role per Unified
 * Algorithm rules, and auto-send protocol messages as the SM advances.
 *
 * Manual injection paths (`deliverIncomingMessage`, `localTrigger`) remain callable from
 * the dev tool so individual SM transitions can still be exercised in isolation.
 */
interface ExchangeV2Runner {
    val events: SharedFlow<ExchangeV2Event>

    /**
     * Events emitted at or after [sinceMs]. The underlying [events] SharedFlow has a replay
     * buffer that persists across sessions, so subscribers may see stale events from prior
     * runs. Consumers that want to scope observation to a single session should snapshot a
     * wall clock (`System.currentTimeMillis()`) before kicking off a new session and pass it
     * here — events emitted with earlier timestamps (from prior sessions) are filtered out.
     */
    fun eventsSince(sinceMs: Long): Flow<ExchangeV2Event> = events.filter { it.timestampMs >= sinceMs }

    val currentState: ExchangeV2State?

    val pairingRole: PairingRole?

    /** Linking code URL for the Presenter side — populated after [startPresent] completes bootstrap. */
    val linkingCode: String?

    /**
     * Whether this device can start as Presenter. Always true: per the Unified Algorithm spec
     * §"Exchange Share Recovery Code" — *"If host has no account yet, create it first."* — a
     * fresh device (no account) can become Host via the Presenter-beats-Scanner tiebreak rule
     * and creates an account during the Exchange Share Recovery Code phase. UI may still read
     * it to vary copy (e.g. "Pair" vs "Pair & create account").
     */
    val canStartAsPresenter: Boolean

    /** Peer device's human-readable name, learned from `recovery_code_available` / `recovery_code_request`. */
    val peerName: String?

    fun startScan(pastedUrl: String)

    fun startPresent()

    fun cancel()

    fun deliverIncomingMessage(message: ExchangeV2Message)

    fun deliverIncomingMessageJson(rawJson: String)

    fun localTrigger(trigger: LocalTrigger)

    fun recordSentMessage(message: ExchangeV2Message)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealExchangeV2Runner @Inject constructor(
    private val smFactory: ExchangeV2StateMachineFactory,
    private val messageParser: ExchangeV2MessageParser,
    private val clock: ExchangeV2Clock,
    private val syncStore: SyncStore,
    private val jweCrypto: SyncJweCrypto,
    private val channel: ExchangeV2Channel,
    private val qrCode: ExchangeV2QrCode,
    private val recoveryCodeProvider: RecoveryCodeProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : ExchangeV2Runner {

    private val _events = MutableSharedFlow<ExchangeV2Event>(replay = REPLAY, extraBufferCapacity = REPLAY)
    override val events: SharedFlow<ExchangeV2Event> = _events.asSharedFlow()

    // Mutex serialises SM mutations + peer-state writes across the poll loop + user clicks.
    private val mutex = Mutex()

    @Volatile private var session: ExchangeV2StateMachine? = null

    @Volatile private var _pairingRole: PairingRole? = null

    @Volatile private var ownChannelId: String? = null

    @Volatile private var ownKeyPair: RsaKeyPair? = null

    @Volatile private var peerChannelId: String? = null

    @Volatile private var peerPublicKey: String? = null

    @Volatile private var peerKind: String? = null

    @Volatile private var peerUserId: String? = null

    @Volatile private var _peerName: String? = null

    @Volatile private var _linkingCode: String? = null

    @Volatile private var pollJob: Job? = null

    @Volatile private var sentOwnAvailability: Boolean = false

    /**
     * Host-side messages that arrive while the Joiner is still showing the user-confirm prompt
     * are buffered here and replayed when the user confirms (Joiner.Confirming → Joiner.Waiting).
     * Without this, a Host with auto-approve enabled races ahead and the Joiner SM rejects the
     * arriving finalisation messages as implicit aborts before the user has tapped Confirm.
     * Cleared on cancel, terminal, or user-deny.
     */
    private val pendingJoinerWaitingMessages = mutableListOf<ExchangeV2Message>()

    override val currentState: ExchangeV2State? get() = session?.currentState
    override val pairingRole: PairingRole? get() = _pairingRole
    override val linkingCode: String? get() = _linkingCode
    override val canStartAsPresenter: Boolean get() = true
    override val peerName: String? get() = _peerName

    // -----------------------------------------------------------------------
    // Entry points
    // -----------------------------------------------------------------------

    override fun startScan(pastedUrl: String) {
        appScope.launch(dispatchers.io()) {
            logcat { "Sync-ExchangeV2: startScan (parsing pasted URL)" }
            val parsed = qrCode.parse(pastedUrl)
            if (parsed !is ExchangeV2CodeParseResult.LinkingV2) {
                emitSessionError("Pasted code is not a v2 linking code: $parsed")
                return@launch
            }
            cancel()
            mutex.withLock {
                _pairingRole = PairingRole.Scanner
                peerChannelId = parsed.channelId
                peerPublicKey = parsed.publicKey
                bootstrapLocked(PairingRole.Scanner) ?: return@withLock // bootstrap reports its own error
                // Scanner already knows the peer; SM starts directly in Negotiating.
                session = smFactory.create(
                    localUserId = syncStore.userId,
                    initialState = ExchangeV2State.Negotiating,
                )
            }
            emitSessionStarted()
            if (!sendHello()) {
                // Couldn't deliver hello — most likely the Presenter's channel has TTL'd out
                // (5 min) or never existed (stale/typo'd code). No point sending availability
                // or polling our own channel; tear down and let the user know.
                emitSessionError(
                    "Pairing aborted — couldn't reach the Presenter. Their session may have expired (5-min TTL). " +
                        "Ask them to Start as Presenter again.",
                )
                cancel()
                return@launch
            }
            sendOwnAvailability()
            startPolling()
        }
    }

    override fun startPresent() {
        appScope.launch(dispatchers.io()) {
            logcat { "Sync-ExchangeV2: startPresent (signedIn=${syncStore.userId != null})" }
            // No pre-flight account check: per spec §"Exchange Share Recovery Code", a Host
            // without an account creates one during the pairing flow. [sendRecoveryCodeResponse]
            // calls createDdgAccountIfNeeded at Host.Sending time for ddg peers.
            cancel()
            mutex.withLock {
                _pairingRole = PairingRole.Presenter
                val keyPair = bootstrapLocked(PairingRole.Presenter) ?: return@withLock
                _linkingCode = qrCode.buildLinkingCode(
                    channelId = ownChannelId!!,
                    publicKeyBase64Url = keyPair.publicKeyBase64,
                )
                // Presenter waits to receive hello before transitioning out of Bootstrapped.
                session = smFactory.create(
                    localUserId = syncStore.userId,
                    initialState = ExchangeV2State.Bootstrapped,
                )
            }
            emitSessionStarted()
            startPolling()
        }
    }

    /**
     * Caller must hold [mutex]. Generates ephemeral keypair, allocates channel_id, and creates
     * the relay channel (with 409 retry). Returns the new keypair on success, null on error
     * (in which case an error event was already emitted).
     */
    private suspend fun bootstrapLocked(role: PairingRole): RsaKeyPair? {
        val keyPair = jweCrypto.generateRsaKeyPair()
        ownKeyPair = keyPair
        repeat(MAX_CHANNEL_CREATE_RETRIES) { attempt ->
            val candidate = UUID.randomUUID().toString()
            when (val r = channel.createChannel(candidate)) {
                is Result.Success -> {
                    ownChannelId = candidate
                    logcat { "Sync-ExchangeV2: bootstrap as $role channel_id=$candidate" }
                    return keyPair
                }
                is Result.Error -> {
                    if (r.code == HTTP_CONFLICT) {
                        logcat { "Sync-ExchangeV2: channel_id $candidate already taken, retrying (${attempt + 1}/$MAX_CHANNEL_CREATE_RETRIES)" }
                    } else {
                        emitSessionError("Failed to create channel: ${r.reason}")
                        return null
                    }
                }
            }
        }
        emitSessionError("Could not allocate unique channel_id after $MAX_CHANNEL_CREATE_RETRIES attempts")
        return null
    }

    override fun cancel() {
        if (session != null || pollJob != null) {
            logcat { "Sync-ExchangeV2: cancel (was in state ${session?.currentState})" }
        }
        pollJob?.cancel()
        pollJob = null
        val toDelete = ownChannelId
        if (toDelete != null) {
            // Best-effort DELETE per Tomek's 2026-05-26 ruling.
            appScope.launch(dispatchers.io()) {
                runCatching { channel.deleteChannel(toDelete) }
            }
        }
        session = null
        _pairingRole = null
        ownChannelId = null
        ownKeyPair = null
        peerChannelId = null
        peerPublicKey = null
        peerKind = null
        peerUserId = null
        _peerName = null
        _linkingCode = null
        sentOwnAvailability = false
        pendingJoinerWaitingMessages.clear()
    }

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    private fun startPolling() {
        val ch = ownChannelId ?: return
        val key = ownKeyPair ?: return
        pollJob = appScope.launch(dispatchers.io()) {
            try {
                channel.poll(ch, key.privateKeyBase64).collect { incoming ->
                    deliverIncomingMessage(incoming)
                }
            } catch (versionTooNew: EnvelopeVersionTooNew) {
                emitSessionError("Peer requires protocol v${versionTooNew.version}; please update this app")
                cancel()
            } catch (decryptFailure: EnvelopeDecryptFailure) {
                // Permanent — the cursor would just re-pull the same broken bytes forever.
                emitSessionError(
                    "Couldn't decrypt a message from the peer (seq=${decryptFailure.seq}): " +
                        "${decryptFailure.cause?.message}. The keys probably don't match — try restarting pairing.",
                )
                cancel()
            } catch (t: Throwable) {
                logcat(ERROR) { "Sync-ExchangeV2: poll loop exited: ${t.message}" }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Inbound message handling + orchestration
    // -----------------------------------------------------------------------

    override fun deliverIncomingMessage(message: ExchangeV2Message) {
        appScope.launch(dispatchers.io()) {
            mutex.withLock { processIncomingLocked(message) }
        }
    }

    private suspend fun processIncomingLocked(message: ExchangeV2Message) {
        val sm = session ?: run {
            logcat { "Sync-ExchangeV2: deliverIncomingMessage ${message.messageType} ignored — no active session" }
            return
        }

        // Race guard: a Host with auto-approve enabled can finish sending its messages before
        // the Joiner's user has tapped Confirm. Stash them and replay after the SM enters Waiting.
        if (sm.currentState == ExchangeV2State.Joiner.Confirming && message.isJoinerWaitingPhase()) {
            logcat { "Sync-ExchangeV2: buffering ${message.messageType} (Joiner still in Confirming; will replay after user confirms)" }
            pendingJoinerWaitingMessages.add(message)
            return
        }

        logcat { "Sync-ExchangeV2: deliverIncomingMessage ${message.messageType} in state ${sm.currentState}" }
        val result = sm.receive(message)
        emit(result.event)

        recordPeerContext(message)
        result.sideEffects.forEach { applySideEffectLocked(it) }

        if (canAutoElectRole(sm, message, result)) {
            autoElectRoleLocked(sm)
        }
        if (canSendOwnAvailability(sm)) {
            sendOwnAvailability()
        }

        if (sm.currentState.isTerminal()) onTerminalReachedLocked(sm.currentState)
    }

    /**
     * Execute one spec-defined [SideEffect] emitted by the state machine. Each effect maps
     * directly to a wire-protocol action or to a composite runner workflow (e.g.
     * [SideEffect.RequestRecoveryCodeShare] kicks off the fetch+send+advance dance).
     */
    private fun applySideEffectLocked(effect: SideEffect) {
        when (effect) {
            SideEffect.SendAwaitingConfirmation -> {
                logcat { "Sync-ExchangeV2: side effect → SendAwaitingConfirmation" }
                sendMessageJson("""{"type":"recovery_code_awaiting_confirmation"}""")
            }
            SideEffect.SendConfirmed -> {
                logcat { "Sync-ExchangeV2: side effect → SendConfirmed" }
                sendMessageJson("""{"type":"recovery_code_confirmed"}""")
            }
            SideEffect.SendDenied -> {
                logcat { "Sync-ExchangeV2: side effect → SendDenied" }
                sendMessageJson("""{"type":"recovery_code_denied"}""")
            }
            SideEffect.RequestRecoveryCodeShare -> {
                logcat { "Sync-ExchangeV2: side effect → RequestRecoveryCodeShare" }
                shareRecoveryCodeAndAdvanceLocked()
            }
        }
    }

    /**
     * Composite of [SideEffect.RequestRecoveryCodeShare]: fetch + send the recovery code
     * (or `recovery_code_unavailable` if it can't be produced), then advance the SM.
     */
    private fun shareRecoveryCodeAndAdvanceLocked() {
        val responseOk = sendRecoveryCodeResponse()
        appScope.launch(dispatchers.io()) {
            mutex.withLock {
                val s = session ?: return@withLock
                if (s.currentState != ExchangeV2State.Host.Sending) return@withLock
                val terminalTrigger = if (responseOk) LocalTrigger.HostSendComplete else LocalTrigger.HostUnavailable
                val r = s.localTrigger(terminalTrigger)
                emit(r.event)
                r.sideEffects.forEach { applySideEffectLocked(it) }
                if (s.currentState.isTerminal()) onTerminalReachedLocked(s.currentState)
            }
        }
    }

    /**
     * Only the success-path messages get buffered when received during Joiner.Confirming:
     * if the peer is telling us they're aborting (denied / unavailable), we let the SM act on
     * that immediately rather than holding the prompt open over a dead session.
     */
    private fun ExchangeV2Message.isJoinerWaitingPhase(): Boolean = when (this) {
        is ExchangeV2Message.RecoveryCodeAwaitingConfirmation,
        is ExchangeV2Message.RecoveryCodeConfirmed,
        is ExchangeV2Message.RecoveryCodeResponse,
        -> true
        else -> false
    }

    private fun recordPeerContext(message: ExchangeV2Message) {
        when (message) {
            is ExchangeV2Message.Hello -> {
                peerChannelId = message.channelId
                peerPublicKey = message.publicKey
            }
            is ExchangeV2Message.RecoveryCodeAvailable -> {
                peerKind = message.kind
                peerUserId = message.userId
                _peerName = message.name
            }
            is ExchangeV2Message.RecoveryCodeRequest -> {
                peerKind = message.kind
                peerUserId = null
                _peerName = message.name
            }
            else -> Unit
        }
    }

    /**
     * Can we auto-elect a role right now? The transition must have been accepted, the SM
     * must be in Negotiating, and we need a peer-side availability message ([RecoveryCodeAvailable]
     * or [RecoveryCodeRequest]) — those carry the peer kind/userId we need to choose a role.
     */
    private fun canAutoElectRole(
        sm: ExchangeV2StateMachine,
        message: ExchangeV2Message,
        receiveResult: TransitionResult,
    ): Boolean {
        if (receiveResult.outcome !is TransitionOutcome.Accepted) return false
        if (sm.currentState != ExchangeV2State.Negotiating) return false
        if (message !is ExchangeV2Message.RecoveryCodeAvailable &&
            message !is ExchangeV2Message.RecoveryCodeRequest
        ) {
            return false
        }
        return true
    }

    /**
     * Perform auto-election. Caller must have established the preconditions via
     * [canAutoElectRole]. Drives the SM via [LocalTrigger.RoleElected] and emits the resulting
     * transition event; also runs any side effects on the elect transition (notably
     * [SideEffect.SendAwaitingConfirmation] for the Host branch).
     */
    private fun autoElectRoleLocked(sm: ExchangeV2StateMachine) {
        val elected = electRole() ?: run {
            logcat { "Sync-ExchangeV2: cannot auto-elect role yet (role=${_pairingRole}, peerKind=$peerKind)" }
            return
        }
        logcat {
            "Sync-ExchangeV2: auto-electing $elected " +
                "(own role=${_pairingRole}, own userId=${syncStore.userId}, peer kind=$peerKind, peer userId=$peerUserId)"
        }
        val electResult = sm.localTrigger(LocalTrigger.RoleElected(elected))
        emit(electResult.event)
        electResult.sideEffects.forEach { applySideEffectLocked(it) }
    }

    /**
     * True when we should send our own availability/request now: SM is in Negotiating, peer
     * has been identified, and we haven't sent already.
     */
    private fun canSendOwnAvailability(sm: ExchangeV2StateMachine): Boolean {
        if (sentOwnAvailability) return false
        if (sm.currentState != ExchangeV2State.Negotiating) return false
        if (peerChannelId == null || peerPublicKey == null) return false
        return true
    }

    private fun electRole(): Role? {
        val ownRole = _pairingRole ?: return null
        val ownUserId = syncStore.userId
        val pKind = peerKind ?: return null
        val pUserId = peerUserId
        return when {
            ownUserId != null && pUserId == null -> Role.Host
            ownUserId == null && pUserId != null -> Role.Joiner
            OWN_DEVICE_KIND == "ddg" && pKind == "3party" -> Role.Host
            OWN_DEVICE_KIND == "3party" && pKind == "ddg" -> Role.Joiner
            ownRole == PairingRole.Presenter -> Role.Host
            else -> Role.Joiner
        }
    }

    override fun deliverIncomingMessageJson(rawJson: String) {
        deliverIncomingMessage(messageParser.parse(rawJson))
    }

    // -----------------------------------------------------------------------
    // Local triggers — drive SM + send outbound messages on Host transitions
    // -----------------------------------------------------------------------

    override fun localTrigger(trigger: LocalTrigger) {
        appScope.launch(dispatchers.io()) {
            mutex.withLock { processLocalTriggerLocked(trigger) }
        }
    }

    private suspend fun processLocalTriggerLocked(trigger: LocalTrigger) {
        val sm = session ?: run {
            logcat { "Sync-ExchangeV2: localTrigger $trigger ignored — no active session" }
            return
        }
        logcat { "Sync-ExchangeV2: localTrigger $trigger in state ${sm.currentState}" }
        val priorState = sm.currentState
        val result = sm.localTrigger(trigger)
        emit(result.event)
        result.sideEffects.forEach { applySideEffectLocked(it) }
        if (priorState == ExchangeV2State.Joiner.Confirming) {
            replayBufferedJoinerMessagesLocked(newState = sm.currentState)
        }
        if (sm.currentState.isTerminal()) onTerminalReachedLocked(sm.currentState)
    }

    /**
     * Clean up after the SM reaches a terminal state. Clears the live-session-only fields so
     * stale UI ("linking code still showing") doesn't suggest the session is reusable, and
     * fires a best-effort DELETE of our own channel (Tomek 2026-05-26).
     */
    private fun onTerminalReachedLocked(terminal: ExchangeV2State) {
        logcat { "Sync-ExchangeV2: session reached terminal state $terminal, clearing" }
        session = null
        _linkingCode = null
        val ch = ownChannelId
        if (ch != null) {
            appScope.launch(dispatchers.io()) { runCatching { channel.deleteChannel(ch) } }
        }
    }

    /**
     * Called only when prior state was [ExchangeV2State.Joiner.Confirming] (the caller is
     * responsible for that check). On user-confirm (transition to Joiner.Waiting), replay any
     * host-side messages that arrived during the prompt. On user-deny (transition to
     * Joiner.AbortedLocal) or any other exit path, discard them — the session is over.
     */
    private suspend fun replayBufferedJoinerMessagesLocked(newState: ExchangeV2State) {
        if (pendingJoinerWaitingMessages.isEmpty()) return
        if (newState == ExchangeV2State.Joiner.Waiting) {
            val buffered = pendingJoinerWaitingMessages.toList()
            pendingJoinerWaitingMessages.clear()
            logcat { "Sync-ExchangeV2: replaying ${buffered.size} buffered Joiner message(s) now that user confirmed" }
            for (m in buffered) {
                processIncomingLocked(m)
                if (session == null) return // terminal reached, stop replay
            }
        } else {
            logcat { "Sync-ExchangeV2: discarding ${pendingJoinerWaitingMessages.size} buffered Joiner message(s) (user denied)" }
            pendingJoinerWaitingMessages.clear()
        }
    }

    // -----------------------------------------------------------------------
    // Outbound message helpers
    // -----------------------------------------------------------------------

    /** Returns true if hello reached the relay; false signals a fatal abort to the caller. */
    private fun sendHello(): Boolean {
        val own = ownChannelId ?: return false
        val peer = peerChannelId ?: return false
        val peerKey = peerPublicKey ?: return false
        val ourKey = ownKeyPair ?: return false
        val json = JSONObject().apply {
            put("type", "hello")
            put("channel_id", own)
            put("public_key", ourKey.publicKeyBase64)
            put("version", OUR_VERSION_STRING)
        }.toString()
        return sendOnWireAndRecord(json, peer, peerKey, ExchangeV2Message.Hello(json, own, ourKey.publicKeyBase64, OUR_VERSION_STRING))
    }

    private fun sendOwnAvailability() {
        if (sentOwnAvailability) return
        val own = ownChannelId ?: return
        val peer = peerChannelId ?: return
        val peerKey = peerPublicKey ?: return
        val userId = syncStore.userId
        if (userId != null) {
            val json = JSONObject().apply {
                put("type", "recovery_code_available")
                put("name", DEVICE_NAME)
                put("kind", OWN_DEVICE_KIND)
                put("user_id", userId)
            }.toString()
            sendOnWireAndRecord(json, peer, peerKey, ExchangeV2Message.RecoveryCodeAvailable(json, userId, DEVICE_NAME, OWN_DEVICE_KIND))
        } else {
            val json = JSONObject().apply {
                put("type", "recovery_code_request")
                put("name", DEVICE_NAME)
                put("kind", OWN_DEVICE_KIND)
            }.toString()
            sendOnWireAndRecord(json, peer, peerKey, ExchangeV2Message.RecoveryCodeRequest(json, DEVICE_NAME, OWN_DEVICE_KIND))
        }
        sentOwnAvailability = true
        // Roll the maybe-auto-elect check forward in case we already received peer availability
        // before we knew our own context.
        val sm = session ?: return
        if (sm.currentState == ExchangeV2State.Negotiating && peerKind != null) {
            val elected = electRole()
            if (elected != null) {
                val r = sm.localTrigger(LocalTrigger.RoleElected(elected))
                emit(r.event)
            }
        }
    }

    /**
     * Send `recovery_code_response` carrying a real recovery code. Returns true on success;
     * false if we couldn't produce a code (in which case we've already sent
     * `recovery_code_unavailable` to the peer + emitted a SessionError event, and the SM
     * should be driven to [ExchangeV2State.Host.Aborted] rather than Done).
     */
    private fun sendRecoveryCodeResponse(): Boolean {
        val peer = peerChannelId ?: return false
        val peerKey = peerPublicKey ?: return false
        // Pick the right recovery code based on peer kind: ddg peers get our DDG recovery
        // code; 3party peers get our 3party access credential's recovery code.
        // Per spec §"Exchange Share Recovery Code":
        //   "If host has no account yet, create it first."
        //   "If this is ddg and peer is 3party, if needed, extend the account."
        // For 3party peers: ensure ddg account exists, then ensure 3party credential exists,
        // then fetch the 3party recovery code. For ddg peers: just ensure ddg account exists.
        // Any provisioning failure falls through to recovery_code_unavailable below.
        //
        // peerKind is set during role election (which requires receiving recovery_code_request
        // or recovery_code_available carrying `kind`). If we reach Host.Sending with no
        // peerKind, something upstream is broken — bail rather than silently assuming ddg.
        val codeResult = when (peerKind) {
            "ddg" -> provisionForDdgPeer()
            "3party" -> provisionForThirdPartyPeer()
            null -> Result.Error(reason = "Host.Sending reached without a known peer kind")
            else -> Result.Error(reason = "Unsupported peer kind '$peerKind'")
        }
        return when (codeResult) {
            is Result.Success -> {
                val recoveryCode = codeResult.data
                val json = JSONObject().apply {
                    put("type", "recovery_code_response")
                    put("recovery_code", recoveryCode)
                }.toString()
                sendOnWireAndRecord(json, peer, peerKey, ExchangeV2Message.RecoveryCodeResponse(json, recoveryCode))
                true
            }
            is Result.Error -> {
                logcat(ERROR) { "Sync-ExchangeV2: recovery code unavailable for peerKind=$peerKind: ${codeResult.reason}" }
                val json = """{"type":"recovery_code_unavailable"}"""
                sendOnWireAndRecord(json, peer, peerKey, ExchangeV2Message.RecoveryCodeUnavailable(json))
                emitSessionError("Couldn't generate a recovery code: ${codeResult.reason}")
                false
            }
        }
    }

    private fun provisionForDdgPeer(): Result<String> =
        when (val provision = recoveryCodeProvider.createDdgAccountIfNeeded()) {
            is Result.Success -> {
                logcat { "Sync-ExchangeV2: ddg account ready, fetching ddg recovery code" }
                recoveryCodeProvider.getDdgRecoveryCode()
            }
            is Result.Error -> {
                logcat(ERROR) { "Sync-ExchangeV2: failed to provision ddg account: ${provision.reason}" }
                Result.Error(reason = "Couldn't create a sync account: ${provision.reason}")
            }
        }

    private fun provisionForThirdPartyPeer(): Result<String> {
        when (val ddg = recoveryCodeProvider.createDdgAccountIfNeeded()) {
            is Result.Success -> Unit
            is Result.Error -> {
                logcat(ERROR) { "Sync-ExchangeV2: failed to provision ddg account for 3party flow: ${ddg.reason}" }
                return Result.Error(reason = "Couldn't create a sync account: ${ddg.reason}")
            }
        }
        when (val extend = recoveryCodeProvider.createThirdPartyCredentialIfNeeded()) {
            is Result.Success -> Unit
            is Result.Error -> {
                logcat(ERROR) { "Sync-ExchangeV2: failed to extend account with 3party credential: ${extend.reason}" }
                return Result.Error(reason = "Couldn't extend account with 3party credential: ${extend.reason}")
            }
        }
        logcat { "Sync-ExchangeV2: ddg account + 3party credential ready, fetching 3party recovery code" }
        return recoveryCodeProvider.getThirdPartyRecoveryCode()
    }

    private fun sendMessageJson(json: String) {
        val peer = peerChannelId ?: return
        val peerKey = peerPublicKey ?: return
        val parsed = messageParser.parse(json)
        sendOnWireAndRecord(json, peer, peerKey, parsed)
    }

    /** Returns true on successful POST, false on transport error (caller decides if fatal). */
    private fun sendOnWireAndRecord(
        messageJson: String,
        peerChannel: String,
        peerKey: String,
        outboundMessage: ExchangeV2Message,
    ): Boolean {
        val own = ownChannelId ?: return false
        return when (val r = channel.sendMessage(messageJson, peerChannel, peerKey, own)) {
            is Result.Success -> {
                recordSentMessage(outboundMessage)
                true
            }
            is Result.Error -> {
                emitSessionError("Failed to send ${outboundMessage.messageType}: ${r.reason}")
                false
            }
        }
    }

    override fun recordSentMessage(message: ExchangeV2Message) {
        logcat { "Sync-ExchangeV2: recordSentMessage ${message.messageType}" }
        emit(ExchangeV2Event.MessageSent(clock.nowMs(), message))
    }

    // -----------------------------------------------------------------------
    // Event emission
    // -----------------------------------------------------------------------

    private fun emit(event: ExchangeV2Event) {
        when (event) {
            is ExchangeV2Event.Transition -> logcat {
                "Sync-ExchangeV2: transition ${event.from} → ${event.to} " +
                    "(trigger=${event.trigger?.messageType ?: event.localTrigger})"
            }
            is ExchangeV2Event.MessageRejected -> logcat {
                "Sync-ExchangeV2: rejected ${event.message.messageType} in ${event.state} reason=${event.reason}"
            }
            is ExchangeV2Event.MessageSent -> Unit
            is ExchangeV2Event.SessionStarted -> logcat {
                val codeLine = event.linkingCode?.let { " linkingCode=$it" } ?: ""
                "Sync-ExchangeV2: session started role=${event.pairingRole} channel_id=${event.ownChannelId}$codeLine"
            }
            is ExchangeV2Event.SessionError -> logcat(ERROR) { "Sync-ExchangeV2: session error: ${event.message}" }
        }
        _events.tryEmit(event)
    }

    private fun emitSessionStarted() {
        val ch = ownChannelId ?: return
        val role = _pairingRole ?: return
        emit(ExchangeV2Event.SessionStarted(clock.nowMs(), role, ch, _linkingCode))
    }

    private fun emitSessionError(message: String) {
        emit(ExchangeV2Event.SessionError(clock.nowMs(), message))
    }

    private fun ExchangeV2State.isTerminal(): Boolean = when (this) {
        SameAccountAbort,
        Host.Aborted,
        Host.Done,
        Joiner.AbortedLocal,
        Joiner.AbortedByHost,
        Joiner.Done,
        -> true
        else -> false
    }

    companion object {
        private const val REPLAY = 100

        // Android is always a ddg-kind device.
        private const val OWN_DEVICE_KIND = "ddg"
        private const val DEVICE_NAME = "Android"
        private const val MAX_CHANNEL_CREATE_RETRIES = 3
        private const val HTTP_CONFLICT = 409
    }
}
