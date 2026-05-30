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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2QrCode
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSyncCodeDispatcherTest {

    private val syncFeature: SyncFeature = mock()
    private val canUseV2: Toggle = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val qrCode: ExchangeV2QrCode = mock()

    // Backing flow that the mocked runner exposes through `events`/`eventsSince`. Keeping it as a
    // standalone reference (rather than reading `mock.events` inside answer lambdas) avoids a
    // Mockito matcher-state issue where re-entrant getter calls during answer dispatch cause
    // ArrayIndexOutOfBoundsException on `invocation.getArgument`.
    private val runnerEventsFlow = MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 0)
    private val runner: ExchangeV2Runner = mock<ExchangeV2Runner>().also {
        whenever(it.events).thenReturn(runnerEventsFlow)
        // Mockito doesn't call interface default methods automatically; wire eventsSince to
        // match the default impl on the interface (filter by timestamp).
        whenever(it.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            runnerEventsFlow.filter { event -> event.timestampMs >= sinceMs }
        }
    }

    private val dispatcher = RealSyncCodeDispatcher(
        syncFeature = syncFeature,
        syncAccountRepository = syncAccountRepository,
        qrCode = qrCode,
        runner = runner,
    )

    private fun setV2(enabled: Boolean) {
        whenever(syncFeature.canUseV2ConnectFlow()).thenReturn(canUseV2)
        whenever(canUseV2.isEnabled()).thenReturn(enabled)
    }

    // ---- FF off: byte-identical to direct parseSyncAuthCode ----

    @Test fun `FF off - returns Legacy with whatever parseSyncAuthCode returned, and never touches qrCode parse`() {
        setV2(false)
        val expectedAuthCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "pk", userId = "u"))
        whenever(syncAccountRepository.parseSyncAuthCode("the-code")).thenReturn(expectedAuthCode)

        val decision = dispatcher.route("the-code") as RouteDecision.Legacy

        assertSame(expectedAuthCode, decision.authCode)
        verify(qrCode, never()).parse(any())
    }

    @Test fun `FF off - v1 Connect codes return Legacy(Connect) unchanged`() {
        setV2(false)
        val connect = SyncAuthCode.Connect(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(connect)

        val decision = dispatcher.route("connect-shape-code") as RouteDecision.Legacy

        assertSame(connect, decision.authCode)
    }

    @Test fun `FF off - v1 Exchange codes return Legacy(Exchange) unchanged`() {
        setV2(false)
        val exchange = SyncAuthCode.Exchange(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(exchange)

        val decision = dispatcher.route("exchange-shape-code") as RouteDecision.Legacy

        assertSame(exchange, decision.authCode)
    }

    @Test fun `FF off - Unknown codes still bubble back as Legacy(Unknown)`() {
        setV2(false)
        val unknown = SyncAuthCode.Unknown("garbage")
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(unknown)

        val decision = dispatcher.route("garbage") as RouteDecision.Legacy

        assertSame(unknown, decision.authCode)
    }

    @Test fun `FF off - even a v2-shaped code is routed via legacy parseSyncAuthCode (no v2 parser touched)`() {
        // Critical safety property: when FF is off, v2 detection is COMPLETELY skipped.
        // A v2 code pasted under FF off becomes Unknown via parseSyncAuthCode - same as
        // production behaviour today.
        setV2(false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("v2-shaped-input"))

        dispatcher.route("https://duckduckgo.com/sync/pairing/#&code2=anything")

        verify(qrCode, never()).parse(any())
        verify(syncAccountRepository).parseSyncAuthCode("https://duckduckgo.com/sync/pairing/#&code2=anything")
    }

    // ---- FF on: v1 shapes still go through legacy stack ----

    @Test fun `FF on but v2 parser returns LinkingV1 - falls back to legacy stack`() {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.LinkingV1)
        val connect = SyncAuthCode.Connect(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(connect)

        val decision = dispatcher.route("v1-code") as RouteDecision.Legacy

        assertSame(connect, decision.authCode)
    }

    @Test fun `FF on but v2 parser returns Unknown - falls back to legacy stack`() {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.Unknown)
        val recovery = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "pk", userId = "u"))
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(recovery)

        val decision = dispatcher.route("recovery-code") as RouteDecision.Legacy

        assertSame(recovery, decision.authCode)
    }

    // ---- FF on: v2 paths take ownership ----

    @Test fun `FF on, v2 LinkingV2 - returns V2InProgress and does NOT call parseSyncAuthCode`() {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        val decision = dispatcher.route("v2-link-url")

        assertTrue("expected V2InProgress, got $decision", decision is RouteDecision.V2InProgress)
        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - flow emits LoggedIn after processCode succeeds`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-1")
            put("secret", "s-1")
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        val decision = dispatcher.route("any") as RouteDecision.V2InProgress
        val outcomes = decision.outcomes.toList()

        assertEquals(1, outcomes.size)
        assertEquals(DispatchOutcome.LoggedIn, outcomes.single())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg with missing user_id - emits Failed without calling processCode`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("secret", "s-1")
            put("cid", "ddg")
        } // no user_id
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))

        val decision = dispatcher.route("any") as RouteDecision.V2InProgress
        val outcome = decision.outcomes.first()

        assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `FF on, v2 RecoveryCode cid=3party - flow calls joinAccountFromThirdPartyRecoveryCode`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-3p")
            put("secret", "s-3p")
            put("cid", "3party")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(Result.Success(true))

        val decision = dispatcher.route("any") as RouteDecision.V2InProgress
        val outcomes = decision.outcomes.toList()

        assertEquals(DispatchOutcome.LoggedIn, outcomes.single())
        verify(syncAccountRepository).joinAccountFromThirdPartyRecoveryCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - ALREADY_SIGNED_IN triggers logoutAndJoinNewAccount and emits LoggedIn`() = runTest {
        // Per spec, the v2 path does NOT surface a v1-style AskToSwitchAccount prompt - the
        // Confirmations phase (or the act of pasting a recovery code) is already the consent
        // step. So when processCode reports ALREADY_SIGNED_IN, the dispatcher transparently
        // calls logoutAndJoinNewAccount with a re-encoded v1-shape recovery code and emits
        // LoggedIn (success) without bubbling AccountSwitchingRequired up to the VM.
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", "s-other")
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(
                code = com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN.code,
                reason = "Already signed in",
            ),
        )
        whenever(syncAccountRepository.logoutAndJoinNewAccount(any())).thenReturn(Result.Success(true))

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.LoggedIn, outcome)

        // Verify the v1-shape recovery code passed to logoutAndJoinNewAccount: base64url-encoded
        // JSON with primary_key+user_id (so the v1-only parseSyncAuthCode inside the repo can
        // re-parse it).
        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(syncAccountRepository).logoutAndJoinNewAccount(captor.capture())
        val decoded = java.util.Base64.getUrlDecoder().decode(captor.firstValue)
        val recovery = JSONObject(String(decoded)).getJSONObject("recovery")
        assertEquals("s-other", recovery.getString("primary_key"))
        assertEquals("u-other", recovery.getString("user_id"))
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - ALREADY_SIGNED_IN then logoutAndJoinNewAccount failure surfaces as Failed`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", "s-other")
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(
                code = com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN.code,
                reason = "Already signed in",
            ),
        )
        whenever(syncAccountRepository.logoutAndJoinNewAccount(any())).thenReturn(
            Result.Error(code = com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED.code, reason = "Login failed"),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.Failed("Login failed"), outcome)
    }

    @Test fun `FF on, v2 RecoveryCode cid=3party - ALREADY_SIGNED_IN does NOT trigger transparent switch (fails as Failed)`() = runTest {
        // 3party upgrade uses a different subroutine (POST /access-credentials/ddg + re-encrypt
        // keys); we can't safely substitute a plain logoutAndJoinNewAccount for it. Spec stays
        // silent on this edge, so we fail rather than guess.
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-3p")
            put("secret", "s-3p")
            put("cid", "3party")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(
            Result.Error(
                code = com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN.code,
                reason = "Already signed in",
            ),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.Failed("Already signed in"), outcome)
        verify(syncAccountRepository, never()).logoutAndJoinNewAccount(any())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - non-ALREADY_SIGNED_IN error still becomes Failed`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u")
            put("secret", "s")
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(code = -1, reason = "Some other error"),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.Failed("Some other error"), outcome)
    }

    @Test fun `FF on, v2 RecoveryCode cid=3party - repository error becomes Failed with reason preserved`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-3p")
            put("secret", "s-3p")
            put("cid", "3party")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(
            Result.Error(reason = "BE rejected"),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.Failed("BE rejected"), outcome)
    }

    @Test fun `FF on, v2 RecoveryCode with unknown cid - emits Failed with the cid in the reason`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u")
            put("secret", "s")
            put("cid", "future-credential")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
        assertTrue((outcome as DispatchOutcome.Failed).reason.contains("future-credential"))
    }

    // ---- Pixel + side effects: dispatcher must NOT fire any of these ----

    @Test fun `Legacy path - dispatcher never invokes processCode (caller owns that)`() {
        setV2(false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Recovery(mock()))

        dispatcher.route("any")

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
        verify(syncAccountRepository, never()).joinAccountFromThirdPartyRecoveryCode(any())
    }

    @Test fun `Legacy path - dispatcher does not start the v2 runner`() {
        setV2(false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("x"))

        dispatcher.route("any")

        verify(runner, never()).startScan(any())
    }

    @Test fun `FF on, LinkingV2 - ignores stale terminal events from prior sessions in the replay cache`() = runTest {
        // Regression: previously the dispatcher would pick up a Joiner.Done event sitting in
        // the runner's events replay cache from a prior successful pairing, treat it as the
        // outcome of THIS session, and call processCode immediately - logging the user in
        // without ever showing the user-confirmation step of the new session.
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        // Pre-seed the class-level events flow with a stale Joiner.Done from a prior session
        // (timestampMs well before this test starts). The class-level eventsSince stub will
        // filter it out based on the dispatcher's sessionStart timestamp.
        val staleJoinerDone = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event.Transition(
            timestampMs = 1L, // ancient
            from = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Joiner.Waiting,
            to = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Joiner.Done,
            trigger = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse(
                rawJson = "{}",
                recoveryCode = "stale-code-from-prior-session",
            ),
            localTrigger = null,
        )
        // Make the backing flow replay-enabled so the stale event is observable.
        val staleFlow = MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 10)
        staleFlow.tryEmit(staleJoinerDone)
        whenever(runner.events).thenReturn(staleFlow)
        whenever(runner.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            staleFlow.filter { event -> event.timestampMs >= sinceMs }
        }

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        // Race the dispatcher's flow against a timeout - without the fix, .first() would
        // immediately pick up the stale Joiner.Done from replay and emit LoggedIn.
        // With the fix, the flow filters out events older than session start and waits
        // for genuine new-session events that never arrive in this test setup.
        val outcome = kotlinx.coroutines.withTimeoutOrNull(100) { decision.outcomes.first() }
        assertEquals(null, outcome) // no premature emission
        // And processCode must NOT have been called for the stale code.
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `FF on, LinkingV2 - runner_startScan deferred until Flow is collected (cold)`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        // Returning V2InProgress alone must not start the session - the caller hasn't decided to
        // commit yet. The runner is only kicked off when the Flow is collected. This matters for
        // safety: callers that do dispatcher.route(...) for inspection won't accidentally bootstrap
        // a channel.
        verify(runner, never()).startScan(any())

        // Now actually collect - but cancel after seeing zero emissions (since runner.events is
        // an empty SharedFlow in this test). We just verify startScan was hit on first collection.
        kotlinx.coroutines.withTimeoutOrNull(50) { decision.outcomes.first() }
        verify(runner).startScan(eq("v2-url"))
    }

    // ---- presentV2() — Presenter-side mapping ----

    private fun transition(
        from: ExchangeV2State,
        to: ExchangeV2State,
        localTrigger: LocalTrigger? = null,
        timestampMs: Long = System.currentTimeMillis(),
    ): ExchangeV2Event.Transition = ExchangeV2Event.Transition(
        timestampMs = timestampMs,
        from = from,
        to = to,
        trigger = null,
        localTrigger = localTrigger,
    )

    private fun sessionStarted(linkingCode: String?, timestampMs: Long = System.currentTimeMillis()) =
        ExchangeV2Event.SessionStarted(
            timestampMs = timestampMs,
            pairingRole = PairingRole.Presenter,
            ownChannelId = "own-channel",
            linkingCode = linkingCode,
        )

    @Test fun `presentV2 emits LinkingCodeReady when runner emits SessionStarted with a linkingCode`() = runTest {
        val flow = dispatcher.presentV2()
        // Start collection in the background; emit the session event after a tiny delay.
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            flow.take(1).collect { outcomes += it }
        }
        runnerEventsFlow.emit(sessionStarted(linkingCode = "https://duckduckgo.com/sync/pairing?code2=abc"))
        job.join()

        assertEquals(1, outcomes.size)
        assertEquals(
            DispatchOutcome.LinkingCodeReady("https://duckduckgo.com/sync/pairing?code2=abc"),
            outcomes.single(),
        )
    }

    @Test fun `presentV2 ignores SessionStarted with null linkingCode (Scanner side leakage protection)`() = runTest {
        // Defensive — startPresent() should always produce a non-null linkingCode, but if the
        // runner ever emits SessionStarted with null we don't want to emit a malformed outcome.
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(sessionStarted(linkingCode = null))
        // Send a follow-up terminal so collection completes.
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Done))
        job.join()

        // First outcome should be LoggedIn (from Host.Done), not a malformed LinkingCodeReady(null/empty).
        assertEquals(DispatchOutcome.LoggedIn, outcomes.single())
    }

    @Test fun `presentV2 emits HostConfirmationRequested with peerName when runner reaches Host_Confirming`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming))
        job.join()

        assertEquals(DispatchOutcome.HostConfirmationRequested(peerName = "Peer Phone"), outcomes.single())
    }

    @Test fun `presentV2 emits LoggedIn when runner reaches Host_Done`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it is DispatchOutcome.LoggedIn || it is DispatchOutcome.Failed }
            }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Sending, to = ExchangeV2State.Host.Done))
            job.await()
        }
        assertEquals(DispatchOutcome.LoggedIn, outcome)
    }

    @Test fun `presentV2 emits Failed user_denied when Host_Aborted carries UserDeniedHost trigger`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.UserDeniedHost,
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.Failed("user_denied"), outcome)
    }

    @Test fun `presentV2 emits Failed host_unavailable when Host_Aborted carries HostUnavailable trigger`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Sending,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.HostUnavailable,
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.Failed("host_unavailable"), outcome)
    }

    @Test fun `presentV2 emits AlreadyConnected when runner reaches SameAccountAbort`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.SameAccountAbort))
            job.await()
        }
        assertEquals(DispatchOutcome.AlreadyConnected, outcome)
    }

    @Test fun `presentV2 emits Failed when runner emits SessionError`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(timestampMs = System.currentTimeMillis(), message = "channel 5xx"),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.Failed("channel 5xx"), outcome)
    }

    @Test fun `presentV2 emits Failed when Joiner_Done arrives without a recovery code`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it !is DispatchOutcome.LinkingCodeReady }
            }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.Done))
            job.await()
        }
        assertEquals(DispatchOutcome.Failed("Pairing completed without a recovery code"), outcome)
    }

    @Test fun `presentV2 emits LoggedIn when Joiner_Done carries a cid=ddg recovery code`() = runTest {
        setV2(true)
        // Build a v2 recovery code payload with cid=ddg.
        val recoveryJson = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("user_id", "u-1")
                    put("secret", "s-1")
                    put("cid", "ddg")
                    put("v", "2.0")
                },
            )
        }.toString()
        val b64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        val responseMessage = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse(
            rawJson = "{}",
            recoveryCode = b64,
        )
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it !is DispatchOutcome.LinkingCodeReady }
            }
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = responseMessage,
                    localTrigger = null,
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.LoggedIn, outcome)
        // Confirm the v1 Recovery shape was constructed and the login was attempted.
        verify(syncAccountRepository).processCode(any(), anyOrNull())
        verify(syncAccountRepository, never()).joinAccountFromThirdPartyRecoveryCode(any())
    }

    @Test fun `presentV2 emits LoggedIn via 3party upgrade when Joiner_Done carries a cid=3party recovery code`() = runTest {
        setV2(true)
        val recoveryJson = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("user_id", "u-3p")
                    put("secret", "s-3p")
                    put("cid", "3party")
                    put("v", "2.0")
                },
            )
        }.toString()
        val b64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        val responseMessage = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse(
            rawJson = "{}",
            recoveryCode = b64,
        )
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(Result.Success(true))

        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it !is DispatchOutcome.LinkingCodeReady }
            }
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = responseMessage,
                    localTrigger = null,
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.LoggedIn, outcome)
        verify(syncAccountRepository).joinAccountFromThirdPartyRecoveryCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `presentV2 emits Failed with cancelled-on-this-device reason when Joiner reaches AbortedLocal`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    localTrigger = LocalTrigger.UserDeniedJoiner,
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.Failed("Pairing cancelled on this device"), outcome)
    }

    @Test fun `presentV2 calls runner_startPresent when Flow is collected`() = runTest {
        val flow = dispatcher.presentV2()
        verify(runner, never()).startPresent()

        // Collect briefly (no events emitted → first() never resolves; rely on timeout).
        withTimeoutOrNull(50) { flow.first() }
        verify(runner).startPresent()
    }

    @Test fun `presentV2 cancels runner when collecting coroutine is cancelled`() = runTest {
        val flow = dispatcher.presentV2()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            flow.collect { /* no-op */ }
        }
        verify(runner).startPresent()

        job.cancel()
        job.join()

        verify(runner).cancel()
    }

    @Test fun `driveV2Linking cancels runner when collecting coroutine is cancelled`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult.LinkingV2(
                channelId = "c",
                publicKey = "k",
                version = "2",
            ),
        )
        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            decision.outcomes.collect { /* no-op */ }
        }
        verify(runner).startScan(any())

        job.cancel()
        job.join()

        verify(runner).cancel()
    }

    @Test fun `presentV2 terminates silently when another SessionStarted with different channel arrives`() = runTest {
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            dispatcher.presentV2().toList(outcomes)
        }
        // First SessionStarted establishes the Flow's own channel.
        runnerEventsFlow.emit(sessionStarted(linkingCode = "code-for-own-channel"))
        // Second SessionStarted with a different channel — simulates preemption by another caller.
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Scanner,
                ownChannelId = "other-channel",
                linkingCode = null,
            ),
        )
        job.join()

        // The Flow should have completed silently after emitting just the first LinkingCodeReady.
        assertEquals(1, outcomes.size)
        assertEquals(DispatchOutcome.LinkingCodeReady("code-for-own-channel"), outcomes.single())
    }

    @Test fun `driveV2Linking terminates silently when another SessionStarted with different channel arrives`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult.LinkingV2(
                channelId = "peer-channel",
                publicKey = "k",
                version = "2",
            ),
        )
        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            decision.outcomes.toList(outcomes)
        }
        // First SessionStarted establishes the Scanner's own channel.
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Scanner,
                ownChannelId = "scanner-own-channel",
                linkingCode = null,
            ),
        )
        // Second SessionStarted with a different channel — simulates preemption.
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Presenter,
                ownChannelId = "different-channel",
                linkingCode = "code",
            ),
        )
        job.join()

        // The Flow should have completed silently — no outcomes emitted (Scanner side never
        // emits anything for the initial SessionStarted because linkingCode=null on its side).
        assertTrue("expected no outcomes, got $outcomes", outcomes.isEmpty())
    }

    @Test fun `presentV2 filters out events from before session start`() = runTest {
        // Seed a stale Host_Done from a prior session with timestamp=1L (well before now).
        val staleFlow = MutableSharedFlow<ExchangeV2Event>(replay = 10)
        staleFlow.tryEmit(
            ExchangeV2Event.Transition(
                timestampMs = 1L,
                from = ExchangeV2State.Host.Sending,
                to = ExchangeV2State.Host.Done,
                trigger = null,
                localTrigger = null,
            ),
        )
        whenever(runner.events).thenReturn(staleFlow)
        whenever(runner.eventsSince(any())).thenAnswer { inv ->
            val since = inv.getArgument<Long>(0)
            staleFlow.filter { it.timestampMs >= since }
        }

        // No fresh event emitted; outcome should never arrive within timeout.
        val outcome = withTimeoutOrNull(100) { dispatcher.presentV2().first() }
        assertEquals(null, outcome)
    }
}
