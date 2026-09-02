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

package com.duckduckgo.sync.internal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.ExchangeResult.Pending
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event.VersionNegotiated
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.exchange.v2.PeerVersionSource
import com.duckduckgo.sync.impl.exchange.v2.RejectReason
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Companion.POLLING_INTERVAL_EXCHANGE_FLOW
import com.duckduckgo.sync.internal.exchange.SyncInternalAdvertisedExchangeV2Version
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncV2PairingDebugViewModel @Inject constructor(
    private val runner: ExchangeV2Runner,
    private val syncStore: SyncStore,
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatcher: SyncCodeDispatcher,
    private val internalAdvertisedVersion: SyncInternalAdvertisedExchangeV2Version,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ViewModel() {

    data class LogRow(
        val id: Long,
        val timestampMs: Long,
        val summary: String,
        val details: LogDetails,
    )

    sealed interface LogDetails {
        val value: String

        data class Json(override val value: String) : LogDetails

        data class PlainText(override val value: String) : LogDetails
    }

    /**
     * Snapshot of the device's sync setup that's relevant to v2 pairing. Read from
     * [SyncStore] at the points where it could have changed (init + after Cancel /
     * terminal). Not reactive — this is a dev tool, the user can tap Cancel to refresh.
     */
    data class AccountStatus(
        val signedIn: Boolean,
        val userId: String?,
        val thirdPartyCredentialCreated: Boolean,
    )

    data class ViewState(
        val currentStateLabel: String = "(no session)",
        val linkingCode: String? = null,
        val rows: List<LogRow> = emptyList(),
        val autoApproveConfirmation: Boolean = true,
        val accountStatus: AccountStatus = AccountStatus(false, null, false),
        val protocolOverride: ExchangeProtocolVersion.V2? = null,
    )

    /**
     * One-shot prompt to show the user. The Activity renders this as an [AlertDialog] and
     * dispatches the result back via [onConfirmationApproved] / [onConfirmationDenied].
     */
    data class ConfirmationRequest(
        val role: Role,
        val peerName: String?,
    )

    /**
     * Emitted once when the session reaches a terminal state. The Activity shows a result
     * alert summarising what happened.
     */
    data class TerminalReached(
        val state: ExchangeV2State,
        val title: String,
        val message: String,
        val isSuccess: Boolean,
    )

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    /**
     * Tear down any in-flight session when the Activity is finishing. ActivityScope VMs
     * survive config changes but [onCleared] runs on real finish (back press, navigation away),
     * which is when we want to dispose of a pairing session that the user has abandoned.
     */
    override fun onCleared() {
        super.onCleared()
        // cancel() suspends until teardown completes; onCleared can't await and viewModelScope is
        // already cancelling, so fire-and-forget the teardown on the app scope (it outlives the VM).
        appScope.launch {
            runner.cancel()
            internalAdvertisedVersion.overrideFlow.value = null
        }
    }

    private val confirmationRequests = Channel<ConfirmationRequest>(Channel.BUFFERED)
    fun confirmations(): Flow<ConfirmationRequest> = confirmationRequests.receiveAsFlow()

    private val terminalReached = Channel<TerminalReached>(Channel.BUFFERED)
    fun terminals(): Flow<TerminalReached> = terminalReached.receiveAsFlow()

    private val toasts = Channel<String>(Channel.BUFFERED)
    fun toasts(): Flow<String> = toasts.receiveAsFlow()

    private var nextRowId: Long = 0L

    init {
        viewState.update { it.copy(accountStatus = readAccountStatus()) }
        viewModelScope.launch(dispatchers.io()) {
            // Snapshot how many events the runner's SharedFlow will replay to us. Those events
            // populate the log row history, but we skip side effects (terminal alerts,
            // confirmation dialogs) for them — otherwise re-entering this Activity after a
            // completed pairing would re-pop the "Pairing complete" dialog from a stale event.
            val replayCount = runner.events.replayCache.size
            var processed = 0
            runner.events.collect { event ->
                appendEvent(event, isReplay = processed < replayCount)
                processed++
            }
        }
        viewModelScope.launch {
            internalAdvertisedVersion.overrideFlow.collect { version ->
                viewState.update { it.copy(protocolOverride = version) }
            }
        }
    }

    /**
     * Delegate routing to [SyncCodeDispatcher] — the single source of truth for v1/v2 dispatch
     * that the production VMs now share. The dispatcher's contract: FF off → byte-identical to
     * direct [SyncAccountRepository.parseSyncAuthCode]; FF on → v2 shapes are taken into
     * ownership and surfaced via a one-shot [DispatchOutcome] Flow.
     *
     * The VM still owns the v1 [SyncAuthCode.Exchange] two-stage polling locally (in
     * [dispatchV1Exchange]) — the dispatcher only handles the routing decision, not the
     * downstream v1 protocol details (preserved byte-for-byte from production).
     */
    fun onRunScanClicked(pastedUrl: String) {
        viewModelScope.launch(dispatchers.io()) {
            appendDevToolLog("Routing pasted code via SyncCodeDispatcher")
            when (val decision = dispatcher.route(pastedUrl)) {
                is RouteDecision.Legacy -> handleLegacyAuthCode(decision.authCode)
                is RouteDecision.V2InProgress -> {
                    appendDevToolLog("Dispatcher took v2 ownership — observing outcomes")
                    decision.outcomes.collect { outcome -> handleV2Outcome(outcome) }
                    refreshState()
                }
            }
        }
    }

    /**
     * Mirrors production v1 handling exactly: Recovery / Connect → [SyncAccountRepository.processCode];
     * Exchange → two-stage post-then-poll loop; Unknown → user-facing error. Identical control
     * flow to [com.duckduckgo.sync.impl.ui.EnterCodeViewModel.authFlow] but logging into the
     * dev-tool log row stream instead of UI commands.
     */
    private suspend fun handleLegacyAuthCode(authCode: SyncAuthCode) {
        appendDevToolLog("Legacy v1 auth code: ${authCode::class.simpleName}")
        when (authCode) {
            is SyncAuthCode.Recovery -> emitProcessCodeResult("v1 recovery", syncAccountRepository.processCode(authCode))
            is SyncAuthCode.Connect -> emitProcessCodeResult("v1 connect", syncAccountRepository.processCode(authCode))
            is SyncAuthCode.Exchange -> dispatchV1Exchange(authCode)
            is SyncAuthCode.Unknown -> {
                appendDevToolLog("Couldn't read the pasted code as v1 or v2")
                toasts.send("Couldn't read the pasted code (no v1 or v2 shape matched)")
            }
        }
        refreshState()
    }

    /**
     * Translate one [DispatchOutcome] emitted by the v2 dispatcher into the dev tool's log +
     * toast surface. Production VMs map these to their own command/error-dialog channels.
     */
    private suspend fun handleV2Outcome(outcome: DispatchOutcome) {
        when (outcome) {
            is DispatchOutcome.LoggedIn -> {
                appendDevToolLog("v2 dispatch → LoggedIn")
                toasts.send("Paired via v2 stack")
            }
            is DispatchOutcome.AlreadyConnected -> {
                appendDevToolLog("v2 dispatch → AlreadyConnected (same-account, spec friendly finish)")
                toasts.send("Already connected (same account)")
            }
            is DispatchOutcome.UpgradeRequired -> {
                appendDevToolLog("v2 dispatch → UpgradeRequired (codeMajor=${outcome.codeMajor})")
                toasts.send("This code requires a newer app version (v${outcome.codeMajor})")
            }
            is DispatchOutcome.Failed -> {
                appendDevToolLog("v2 dispatch → Failed: ${outcome.reason}")
                toasts.send("v2 dispatch failed: ${outcome.reason}")
            }
            is DispatchOutcome.JoinerConfirmationRequested,
            is DispatchOutcome.HostConfirmationRequested,
            -> {
                // The dev tool already drives confirmation via its direct runner.events
                // observation ([maybeHandleConfirmingTransition]). Ignoring the dispatcher's
                // intermediate emission here avoids double-prompting.
                appendDevToolLog("v2 dispatch → ${outcome::class.simpleName} (handled by dev tool's direct observation)")
            }
            is DispatchOutcome.LinkingCodeReady -> {
                // No-op: the dev tool drives Presenter sessions directly via runner.startPresent()
                // (onRunPresentClicked), not through dispatcher.presentV2(). This branch only exists
                // to keep the when block exhaustive.
                appendDevToolLog("v2 dispatch → LinkingCodeReady (handled by dev tool's direct observation)")
            }
        }
    }

    /**
     * v1 Exchange is a two-stage scanner flow:
     *   1. [SyncAccountRepository.processCode] (→ `onInvitationCodeReceived`) posts our
     *      encrypted device details to the presenter's relay slot.
     *   2. Poll [SyncAccountRepository.pollForRecoveryCodeAndLogin] until the presenter
     *      replies with the encrypted recovery code, which the repo then uses to log us in.
     *
     * Mirrors [SyncLoginViewModel.pollForRecoveryKey] / [SyncWithAnotherActivityViewModel]
     * — same polling cadence, same result handling. Without step 2 the device finishes step
     * 1 with "OK" but never actually logs in (the original bug).
     */
    private suspend fun dispatchV1Exchange(code: SyncAuthCode.Exchange) {
        when (val postResult = syncAccountRepository.processCode(code)) {
            is Result.Error -> {
                appendDevToolLog("v1 exchange step 1 (post device details) failed: ${postResult.reason}")
                toasts.send("v1 exchange failed: ${postResult.reason}")
                return
            }
            is Result.Success -> {
                appendDevToolLog("v1 exchange step 1 (post device details) OK — polling for recovery code")
            }
        }
        var polling = true
        while (polling) {
            delay(POLLING_INTERVAL_EXCHANGE_FLOW)
            when (val pollResult = syncAccountRepository.pollForRecoveryCodeAndLogin()) {
                is Result.Success -> when (pollResult.data) {
                    is Pending -> Unit // keep polling
                    is LoggedIn -> {
                        polling = false
                        appendDevToolLog("v1 exchange: logged in via received recovery code")
                        toasts.send("Paired via v1 exchange stack")
                    }
                    is AccountSwitchingRequired -> {
                        // Production prompts the user; dev tool stops short of destructive
                        // account switching and just surfaces the situation.
                        polling = false
                        appendDevToolLog("v1 exchange: peer offered a different account (skipped — account switching not wired here)")
                        toasts.send("v1 exchange: account switching required — not wired in dev tool")
                    }
                }
                is Result.Error -> {
                    polling = false
                    appendDevToolLog("v1 exchange poll failed: ${pollResult.reason}")
                    toasts.send("v1 exchange poll failed: ${pollResult.reason}")
                }
            }
        }
    }

    private suspend fun emitProcessCodeResult(label: String, result: Result<Boolean>) {
        when (result) {
            is Result.Success -> {
                appendDevToolLog("$label: OK")
                toasts.send("Paired via $label stack")
            }
            is Result.Error -> {
                appendDevToolLog("$label failed: ${result.reason}")
                toasts.send("$label failed: ${result.reason}")
            }
        }
    }

    /**
     * Push a synthetic row into the event log so v1-fallback progress is visible alongside
     * native v2 [ExchangeV2Event]s. Reuses [LogRow] with the message in both summary and details.
     * Also tees to logcat with a `Sync-V2Debug:` prefix so the fallback path is traceable
     * end-to-end (the in-screen log alone misses the eyes of anyone watching logcat).
     */
    private fun appendDevToolLog(message: String) {
        logcat { "Sync-V2Debug: $message" }
        viewState.update { current ->
            current.copy(
                rows = current.rows + LogRow(
                    id = nextRowId++,
                    timestampMs = System.currentTimeMillis(),
                    summary = message,
                    details = LogDetails.PlainText(message),
                ),
            )
        }
    }

    fun onRunPresentClicked() {
        viewModelScope.launch(dispatchers.io()) {
            runner.startPresent()
            refreshState()
        }
    }

    /** True when this device has a sync account whose recovery code we could share with a Joiner. */
    fun canStartAsPresenter(): Boolean = runner.canStartAsPresenter

    /**
     * Toggle account state: create a fresh sync account if signed out, or log out (and tear
     * down the current session) if signed in. Status row refreshes either way.
     */
    fun onSignInOutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val signedIn = syncStore.userId != null
            val result = if (signedIn) {
                // If a pairing session was running on this device's old credentials, kill it
                // before logout invalidates the recovery code under us.
                runner.cancel()
                val deviceId = syncStore.deviceId.orEmpty()
                syncAccountRepository.logout(deviceId)
            } else {
                syncAccountRepository.createAccount()
            }
            val action = if (signedIn) "Sign out" else "Create account"
            when (result) {
                is Result.Success -> toasts.send("$action: OK")
                is Result.Error -> toasts.send("$action failed: ${result.reason}")
            }
            refreshState()
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch(dispatchers.io()) {
            runner.cancel()
            refreshState()
        }
    }

    fun onClearLogClicked() {
        viewState.update { it.copy(rows = emptyList()) }
    }

    fun onAutoApproveToggled(checked: Boolean) {
        viewState.update { it.copy(autoApproveConfirmation = checked) }
    }

    fun onProtocolOverrideSelected(version: ExchangeProtocolVersion.V2?) {
        internalAdvertisedVersion.overrideFlow.value = version
        appendDevToolLog("Protocol version override → ${labelFor(version)} (applies to the next session)")
    }

    fun labelFor(version: ExchangeProtocolVersion.V2?): String = when (version) {
        null -> "Default (${internalAdvertisedVersion.defaultVersion().prettyPrint()})"
        else -> version.prettyPrint()
    }

    fun onConfirmationApproved(role: Role) {
        fireLocalTrigger(if (role == Role.Host) LocalTrigger.UserConfirmedHost else LocalTrigger.UserConfirmedJoiner)
    }

    fun onConfirmationDenied(role: Role) {
        fireLocalTrigger(if (role == Role.Host) LocalTrigger.UserDeniedHost else LocalTrigger.UserDeniedJoiner)
    }

    private fun fireLocalTrigger(trigger: LocalTrigger) {
        viewModelScope.launch(dispatchers.io()) {
            runner.localTrigger(trigger)
            refreshState()
        }
    }

    private fun appendEvent(event: ExchangeV2Event, isReplay: Boolean = false) {
        val row = LogRow(
            id = nextRowId++,
            timestampMs = event.timestampMs,
            summary = summarise(event),
            details = detailsFor(event),
        )
        viewState.update { current ->
            current.copy(
                rows = current.rows + row,
                currentStateLabel = buildStateLabel(),
                linkingCode = runner.linkingCode,
                // Refresh on every event so runner-driven account changes (e.g. on-demand
                // account creation at Host.Sending per spec §"Exchange Share Recovery Code")
                // surface immediately, not on next manual refresh.
                accountStatus = readAccountStatus(),
            )
        }
        if (!isReplay) {
            maybeHandleConfirmingTransition(event)
            maybeEmitTerminalAlert(event)
            maybeToastSessionError(event)
            maybeLoginAfterJoinerJoining(event)
        }
    }

    /**
     * When the runner reaches [ExchangeV2State.Joiner.Joining] from a session that was started
     * via [onRunPresentClicked] (i.e. not routed through [SyncCodeDispatcher]), the dispatcher's
     * own login Flow never runs and nothing else drives a login from the received recovery code.
     * This handler closes that gap. Scoped to Presenter sessions so a Scanner session, which does
     * go through the dispatcher, isn't logged in twice and doesn't report two conflicting outcomes.
     *
     * Reports the result back to the peer, which is what moves the session off Joining and on to a
     * Joiner terminal. A missing or blank recovery code reports login_failed rather than leaving
     * the session parked in Joining until the deadline, matching the production dispatcher.
     */
    private fun maybeLoginAfterJoinerJoining(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.Transition) return
        if (event.to != ExchangeV2State.Joiner.Joining) return
        if (runner.pairingRole != PairingRole.Presenter) return
        val received = (event.trigger as? ExchangeV2Message.RecoveryCodeResponse)?.recoveryCode
        viewModelScope.launch(dispatchers.io()) {
            val loggedIn = if (received.isNullOrBlank()) {
                appendDevToolLog("Joiner.Joining: no recovery code in the response, reporting login_failed")
                false
            } else {
                loginWithReceivedRecoveryCode(received)
            }
            val reason = if (loggedIn) {
                ExchangeV2Message.RecoveryCodeDone.Reason.Success
            } else {
                ExchangeV2Message.RecoveryCodeDone.Reason.LoginFailed
            }
            runner.localTrigger(LocalTrigger.JoinerJoinComplete(reason)).join()
            refreshState()
        }
    }

    private suspend fun loginWithReceivedRecoveryCode(received: String): Boolean {
        appendDevToolLog("Joiner.Joining: driving login from received recovery code")
        return when (val decision = dispatcher.route(received)) {
            is RouteDecision.V2InProgress -> {
                var success = false
                decision.outcomes.collect { outcome ->
                    handleV2Outcome(outcome)
                    if (outcome is DispatchOutcome.LoggedIn || outcome is DispatchOutcome.AlreadyConnected) success = true
                }
                success
            }
            is RouteDecision.Legacy -> when (decision.authCode) {
                is SyncAuthCode.Recovery -> {
                    val result = syncAccountRepository.processCode(decision.authCode)
                    emitProcessCodeResult("Joiner.Joining login", result)
                    result is Result.Success
                }
                else -> {
                    appendDevToolLog("Joiner.Joining: received code wasn't a Recovery shape, can't auto-login")
                    toasts.send("Joiner.Joining: unexpected code shape, manual login required")
                    false
                }
            }
        }
    }

    /** Surface transport-level errors (failed sends, version mismatches) so they don't just bury in the log. */
    private fun maybeToastSessionError(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.SessionError) return
        viewModelScope.launch { toasts.send(event.message) }
    }

    private fun maybeEmitTerminalAlert(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.Transition) return
        val terminal = describeTerminal(event) ?: return
        viewModelScope.launch { terminalReached.send(terminal) }
    }

    private fun describeTerminal(event: ExchangeV2Event.Transition): TerminalReached? {
        val (title, isSuccess) = when (event.to) {
            ExchangeV2State.Host.Done -> if (event.isPeerFailure()) {
                "✗ Join failed on the peer" to false
            } else {
                "✓ Pairing complete (Host)" to true
            }
            ExchangeV2State.Joiner.Done -> "✓ Pairing complete (Joiner)" to true
            ExchangeV2State.Joiner.JoinFailed -> "✗ Join failed (Joiner)" to false
            ExchangeV2State.Host.Aborted -> "✗ Pairing aborted (Host)" to false
            ExchangeV2State.Joiner.AbortedLocal -> "✗ Pairing aborted (Joiner)" to false
            ExchangeV2State.Joiner.AbortedByHost -> "✗ Pairing aborted by peer" to false
            ExchangeV2State.SameAccountAbort -> "✗ Same-account abort" to false
            ExchangeV2State.Aborted -> "✗ Negotiation aborted" to false
            else -> return null
        }
        return TerminalReached(
            state = event.to,
            title = title,
            message = "Check the event log for details.",
            isSuccess = isSuccess,
        )
    }

    private fun ExchangeV2Event.Transition.isPeerFailure(): Boolean {
        val reason = (trigger as? ExchangeV2Message.RecoveryCodeDone)?.reason ?: return false
        return reason != ExchangeV2Message.RecoveryCodeDone.Reason.Success
    }

    private fun maybeHandleConfirmingTransition(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.Transition) return
        val role = when (event.to) {
            ExchangeV2State.Host.Confirming -> Role.Host
            ExchangeV2State.Joiner.Confirming -> Role.Joiner
            else -> return
        }
        if (viewState.value.autoApproveConfirmation) {
            val trigger = if (role == Role.Host) LocalTrigger.UserConfirmedHost else LocalTrigger.UserConfirmedJoiner
            viewModelScope.launch(dispatchers.io()) { runner.localTrigger(trigger) }
        } else {
            viewModelScope.launch {
                confirmationRequests.send(ConfirmationRequest(role = role, peerName = runner.peerName))
            }
        }
    }

    private fun refreshState() {
        viewState.update {
            it.copy(
                currentStateLabel = buildStateLabel(),
                linkingCode = runner.linkingCode,
                accountStatus = readAccountStatus(),
            )
        }
    }

    private fun readAccountStatus(): AccountStatus {
        val userId = syncStore.userId
        val signedIn = userId != null
        val thirdParty = syncStore.scopedPassword != null
        return AccountStatus(
            signedIn = signedIn,
            userId = userId,
            thirdPartyCredentialCreated = signedIn && thirdParty,
        )
    }

    private fun buildStateLabel(): String {
        val state = labelFor(runner.currentState)
        val role = runner.pairingRole ?: return state
        return "$state · pairing as $role"
    }

    private fun summarise(event: ExchangeV2Event): String = when (event) {
        is ExchangeV2Event.Transition -> buildString {
            append("Transition ${labelFor(event.from)} → ${labelFor(event.to)} [")
            val trigger = event.trigger
            val localTrigger = event.localTrigger
            when {
                trigger != null -> {
                    append("msg=${trigger.messageType}")
                    appendPeerDetails(trigger)
                }
                localTrigger != null -> append("local=${labelFor(localTrigger)}")
                else -> append("no trigger")
            }
            append(']')
        }
        is ExchangeV2Event.MessageSent -> buildString {
            append("Sent ${event.message.messageType}")
            appendPeerDetails(event.message)
        }
        is ExchangeV2Event.MessageRejected -> buildString {
            append("${labelFor(event.reason)} on ${event.message.messageType}")
            appendPeerDetails(event.message)
            append(" in ${labelFor(event.state)}")
        }
        is ExchangeV2Event.SessionStarted -> buildString {
            append("Session started as ${event.pairingRole} ownChannelId=${event.ownChannelId}")
            event.linkingCode?.let { append(" linkingCode=$it") }
        }
        is ExchangeV2Event.SessionError -> "Session error: ${event.message}"
        is VersionNegotiated -> "Negotiated Exchange protocol ${event.negotiatedVersion.prettyPrint()}"
    }

    private fun StringBuilder.appendPeerDetails(message: ExchangeV2Message) {
        when (message) {
            is ExchangeV2Message.RecoveryCodeAvailable -> append(" name=${message.name} kind=${message.kind} user_id=${message.userId}")
            is ExchangeV2Message.RecoveryCodeRequest -> append(" name=${message.name} kind=${message.kind}")
            is ExchangeV2Message.RecoveryCodeDone -> append(" reason=${message.reason.value}")
            else -> Unit
        }
    }

    private fun detailsFor(event: ExchangeV2Event): LogDetails = when (event) {
        is ExchangeV2Event.Transition -> when (val trigger = event.trigger) {
            null -> LogDetails.PlainText("local trigger: ${event.localTrigger?.let(::labelFor) ?: "none"}")
            else -> LogDetails.Json(trigger.rawJson)
        }
        is ExchangeV2Event.MessageSent -> LogDetails.Json(event.message.rawJson)
        is ExchangeV2Event.MessageRejected -> LogDetails.Json(event.message.rawJson)
        is ExchangeV2Event.SessionStarted -> when (val linkingCode = event.linkingCode) {
            null -> LogDetails.PlainText("no linking code — Scanner side")
            else -> LogDetails.PlainText(linkingCode)
        }
        is ExchangeV2Event.SessionError -> LogDetails.PlainText(event.message)
        is VersionNegotiated -> LogDetails.PlainText(
            "ours=${event.ourVersion.prettyPrint()}, peer=${event.peerVersion.prettyPrint()} [${labelFor(event.peerSource)}]",
        )
    }

    private fun labelFor(state: ExchangeV2State?): String = when (state) {
        null -> "(no session)"
        ExchangeV2State.Bootstrapped -> "Bootstrapped"
        ExchangeV2State.Negotiating -> "Negotiating"
        ExchangeV2State.SameAccountAbort -> "SameAccountAbort"
        ExchangeV2State.Aborted -> "Aborted"
        ExchangeV2State.Host.Confirming -> "Host.Confirming"
        ExchangeV2State.Host.Sending -> "Host.Sending"
        ExchangeV2State.Host.AwaitingStatus -> "Host.AwaitingStatus"
        ExchangeV2State.Host.Aborted -> "Host.Aborted"
        ExchangeV2State.Host.Done -> "Host.Done"
        ExchangeV2State.Joiner.Confirming -> "Joiner.Confirming"
        ExchangeV2State.Joiner.Waiting -> "Joiner.Waiting"
        ExchangeV2State.Joiner.Joining -> "Joiner.Joining"
        ExchangeV2State.Joiner.AbortedLocal -> "Joiner.AbortedLocal"
        ExchangeV2State.Joiner.AbortedByHost -> "Joiner.AbortedByHost"
        ExchangeV2State.Joiner.Done -> "Joiner.Done"
        ExchangeV2State.Joiner.JoinFailed -> "Joiner.JoinFailed"
    }

    private fun labelFor(trigger: LocalTrigger): String = when (trigger) {
        LocalTrigger.UserConfirmedHost -> "UserConfirmedHost"
        LocalTrigger.UserDeniedHost -> "UserDeniedHost"
        LocalTrigger.UserConfirmedJoiner -> "UserConfirmedJoiner"
        LocalTrigger.UserDeniedJoiner -> "UserDeniedJoiner"
        is LocalTrigger.HostSendComplete -> "HostSendComplete(${trigger.negotiatedVersion.prettyPrint()})"
        is LocalTrigger.JoinerJoinComplete -> "JoinerJoinComplete(${trigger.reason.value})"
        LocalTrigger.HostUnavailable -> "HostUnavailable"
        is LocalTrigger.RoleElected -> "RoleElected(${trigger.role})"
    }

    private fun labelFor(reason: RejectReason): String = when (reason) {
        RejectReason.ImplicitAbort -> "Aborted"
        RejectReason.SameAccount -> "SameAccountAbort"
        RejectReason.UnknownMessageDropped -> "Dropped (unknown)"
    }

    private fun labelFor(peerSource: PeerVersionSource): String = when (peerSource) {
        PeerVersionSource.LinkingCode -> "linking_code"
        PeerVersionSource.HelloMessage -> "hello_message"
    }
}
