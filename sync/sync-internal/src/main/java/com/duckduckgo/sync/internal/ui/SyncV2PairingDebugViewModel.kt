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
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.internal.exchange.SyncInternalAdvertisedExchangeV2Version
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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

    data class AccountStatus(
        val signedIn: Boolean,
        val userId: String?,
        val thirdPartyCredentialCreated: Boolean,
    )

    data class ViewState(
        val currentStateLabel: String = "(no session)",
        val linkingCode: String? = null,
        val rows: List<LogRow> = emptyList(),
        val activeCategories: Set<LogRow.Category> = LogRow.Category.entries.toSet(),
        val autoApproveConfirmation: Boolean = true,
        val accountStatus: AccountStatus = AccountStatus(false, null, false),
        val protocolOverride: ExchangeProtocolVersion.V2? = null,
    ) {
        val visibleRows: List<LogRow> get() = rows.filter { it.eventType.category in activeCategories }
    }

    data class ConfirmationRequest(
        val role: Role,
        val peerName: String?,
    )

    data class TerminalReached(
        val state: ExchangeV2State,
        val title: String,
        val message: String,
        val isSuccess: Boolean,
    )

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    private val confirmationRequests = Channel<ConfirmationRequest>(Channel.BUFFERED)
    fun confirmations(): Flow<ConfirmationRequest> = confirmationRequests.receiveAsFlow()

    private val terminalReached = Channel<TerminalReached>(Channel.BUFFERED)
    fun terminals(): Flow<TerminalReached> = terminalReached.receiveAsFlow()

    private val toasts = Channel<String>(Channel.BUFFERED)
    fun toasts(): Flow<String> = toasts.receiveAsFlow()

    private val nextRowId = AtomicLong(0L)

    private var presentSessionJob: Job? = null
    private var scanSessionJob: Job? = null

    private fun sessionJobs(): List<Job> = listOfNotNull(presentSessionJob, scanSessionJob)

    init {
        viewState.update { current ->
            current.copy(accountStatus = readAccountStatus())
        }

        viewModelScope.launch(dispatchers.io()) {
            runner.events.collect { event ->
                val row = LogRow.from(event, id = nextRowId.getAndIncrement())
                viewState.update { current ->
                    current.copy(
                        rows = current.rows + row,
                        currentStateLabel = buildStateLabel(),
                        // Refresh on every event so runner-driven account changes (e.g. on-demand
                        // account creation at Host.Sending per spec §"Exchange Share Recovery Code")
                        // surface immediately, not on next manual refresh.
                        accountStatus = readAccountStatus(),
                    )
                }
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            internalAdvertisedVersion.overrideFlow.collect { version ->
                viewState.update { current ->
                    current.copy(protocolOverride = version)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appScope.launch {
            sessionJobs().forEach { it.cancel() }
            internalAdvertisedVersion.overrideFlow.value = null
        }
    }

    fun onRunScanClicked(pastedUrl: String) {
        val previousSessions = sessionJobs()
        scanSessionJob = viewModelScope.launch(dispatchers.io()) {
            previousSessions.forEach { it.cancelAndJoin() }
            appendDevToolLog("Routing pasted code via SyncCodeDispatcher")
            when (val decision = dispatcher.route(pastedUrl)) {
                is RouteDecision.Legacy -> handleLegacyAuthCode(decision.authCode)
                is RouteDecision.V2InProgress -> {
                    appendDevToolLog("Dispatcher took v2 ownership — observing outcomes")
                    launchSessionSideEffects(System.currentTimeMillis())
                    decision.outcomes.collect { outcome -> handleV2Outcome(outcome) }
                    refreshState()
                }
            }
        }
    }

    fun canStartAsPresenter(): Boolean = runner.canStartAsPresenter

    fun onRunPresentClicked() {
        val previousSessions = sessionJobs()
        presentSessionJob = viewModelScope.launch(dispatchers.io()) {
            previousSessions.forEach { it.cancelAndJoin() }
            launchSessionSideEffects(System.currentTimeMillis())
            appendDevToolLog("Starting Presenter session via SyncCodeDispatcher")
            try {
                dispatcher.presentV2().collect { outcome -> handleV2Outcome(outcome) }
                refreshState()
            } finally {
                // The linking code is only valid while this session is alive; clear it on any
                // exit — terminal outcome, Cancel/sign-out cancellation, or being replaced by
                // a newer session (whose cancelAndJoin runs this before it starts).
                viewState.update { it.copy(linkingCode = null) }
            }
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch(dispatchers.io()) {
            sessionJobs().forEach { it.cancelAndJoin() }
            refreshState()
        }
    }

    fun onSignInOutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val signedIn = syncStore.userId != null
            val result = if (signedIn) {
                sessionJobs().forEach { it.cancelAndJoin() }
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

    fun onConfirmationApproved(role: Role) {
        when (role) {
            Role.Host -> dispatcher.confirmHost()
            Role.Joiner -> dispatcher.confirmJoiner()
        }
    }

    fun onConfirmationDenied(role: Role) {
        when (role) {
            Role.Host -> dispatcher.denyHost()
            Role.Joiner -> dispatcher.denyJoiner()
        }
    }

    fun onAutoApproveToggled(checked: Boolean) {
        viewState.update { it.copy(autoApproveConfirmation = checked) }
    }

    fun onProtocolOverrideSelected(version: ExchangeProtocolVersion.V2?) {
        internalAdvertisedVersion.overrideFlow.value = version
        appendDevToolLog("Protocol version override → ${labelFor(version)}")
    }

    fun labelFor(version: ExchangeProtocolVersion.V2?): String = when (version) {
        null -> "Default (${internalAdvertisedVersion.defaultVersion().prettyPrint()})"
        else -> version.prettyPrint()
    }

    fun onClearLogClicked() {
        viewState.update { it.copy(rows = emptyList()) }
    }

    fun onLogFilterChanged(activeCategories: Set<LogRow.Category>) {
        viewState.update { it.copy(activeCategories = activeCategories) }
    }

    private fun CoroutineScope.launchSessionSideEffects(sessionStartMs: Long): Job = launch {
        runner.eventsSince(sessionStartMs)
            .transformWhile { event ->
                emit(event)
                event !is ExchangeV2Event.SessionEnded && event !is ExchangeV2Event.SessionError
            }
            .collect { event ->
                maybeHandleConfirmingTransition(event)
                maybeEmitTerminalAlert(event)
                maybeToastSessionError(event)
            }
    }

    private fun maybeHandleConfirmingTransition(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.Transition) return
        val role = when (event.to) {
            ExchangeV2State.Host.Confirming -> Role.Host
            ExchangeV2State.Joiner.Confirming -> Role.Joiner
            else -> return
        }
        if (viewState.value.autoApproveConfirmation) {
            onConfirmationApproved(role)
        } else {
            viewModelScope.launch {
                confirmationRequests.send(ConfirmationRequest(role = role, peerName = runner.peerName))
            }
        }
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

    private fun maybeToastSessionError(event: ExchangeV2Event) {
        if (event !is ExchangeV2Event.SessionError) return
        viewModelScope.launch { toasts.send(event.message) }
    }

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
                appendDevToolLog("v2 dispatch → LinkingCodeReady")
                viewState.update { it.copy(linkingCode = outcome.linkingCode) }
            }
        }
    }

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

    private fun appendDevToolLog(message: String) {
        logcat { "Sync-V2Debug: $message" }
        val row = LogRow.devTool(
            id = nextRowId.getAndIncrement(),
            message = message,
        )
        viewState.update { current ->
            current.copy(rows = current.rows + row)
        }
    }

    private fun refreshState() {
        viewState.update {
            it.copy(
                currentStateLabel = buildStateLabel(),
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
        val state = runner.currentState.debugLabel()
        val role = runner.pairingRole ?: return state
        return "$state · pairing as $role"
    }

    private companion object {
        val POLLING_INTERVAL_EXCHANGE_FLOW = 2.seconds
    }
}
