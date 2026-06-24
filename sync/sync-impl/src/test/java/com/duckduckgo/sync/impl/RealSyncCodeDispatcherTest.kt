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
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val runnerEventsFlow = MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 0)
    private val runner: ExchangeV2Runner = mock<ExchangeV2Runner>().also {
        whenever(it.events).thenReturn(runnerEventsFlow)
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
        setV2(false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("v2-shaped-input"))

        dispatcher.route("https://duckduckgo.com/sync/pairing/#&code2=anything")

        verify(qrCode, never()).parse(any())
        verify(syncAccountRepository).parseSyncAuthCode("https://duckduckgo.com/sync/pairing/#&code2=anything")
    }

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

    @Test fun `FF on, v2 LinkingV2 - returns V2InProgress and does NOT call parseSyncAuthCode`() {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        val decision = dispatcher.route("v2-link-url")

        assertTrue("expected V2InProgress, got $decision", decision is RouteDecision.V2InProgress)
        assertEquals(SyncCodeType.LINKING, (decision as RouteDecision.V2InProgress).codeType)
        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }

    @Test fun `isV2ExchangeUnderway is false at Bootstrapped or no session, true once exchanging`() {
        whenever(runner.currentState).thenReturn(null)
        assertFalse(dispatcher.isV2ExchangeUnderway())
        whenever(runner.currentState).thenReturn(ExchangeV2State.Bootstrapped)
        assertFalse(dispatcher.isV2ExchangeUnderway())
        whenever(runner.currentState).thenReturn(ExchangeV2State.Negotiating)
        assertTrue(dispatcher.isV2ExchangeUnderway())
        whenever(runner.currentState).thenReturn(ExchangeV2State.Joiner.Waiting)
        assertTrue(dispatcher.isV2ExchangeUnderway())
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
        assertEquals(SyncCodeType.RECOVERY, decision.codeType)
        val outcomes = decision.outcomes.toList()

        assertEquals(1, outcomes.size)
        assertEquals(DispatchOutcome.LoggedIn(SetupPath.RECOVERY), outcomes.single())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - base64url secret is normalised to standard base64 for v1 login`() = runTest {
        // Spec 1214802412121967: the v2 wire `secret` is base64url; v1 login decodes as standard base64.
        setV2(true)
        val base64urlSecret = "rUzlGqLLlbonAC_zIeh1nrCmuDsDAn6UooUUDz-6x3o"
        val expectedBytes = java.util.Base64.getUrlDecoder().decode(base64urlSecret)
        val rawJson = JSONObject().apply {
            put("user_id", "u-1")
            put("secret", base64urlSecret)
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.toList()

        val captor = org.mockito.kotlin.argumentCaptor<SyncAuthCode>()
        verify(syncAccountRepository).processCode(captor.capture(), anyOrNull())
        val primaryKey = (captor.firstValue as SyncAuthCode.Recovery).b64Code.primaryKey
        assertFalse("primaryKey must be standard base64 (no '-'): $primaryKey", primaryKey.contains('-'))
        assertFalse("primaryKey must be standard base64 (no '_'): $primaryKey", primaryKey.contains('_'))
        assertArrayEquals(expectedBytes, java.util.Base64.getDecoder().decode(primaryKey))
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg with missing user_id - emits Failed without calling processCode`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("secret", "s-1")
            put("cid", "ddg")
        }
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

        assertEquals(DispatchOutcome.LoggedIn(SetupPath.RECOVERY), outcomes.single())
        verify(syncAccountRepository).joinAccountFromThirdPartyRecoveryCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `FF on, v2 RecoveryCode cid=3party - re-encoded code is canonical JSON without escaped slashes`() = runTest {
        setV2(true)
        val secretWithSlashes = "apZ+7PAe89rDhuG4DRyi/M3zU2/D5DZNdRsR3RM6Ujw="
        val rawJson = JSONObject().apply {
            put("user_id", "u-3p")
            put("secret", secretWithSlashes)
            put("cid", "3party")
            put("v", "2.0")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(Result.Success(true))

        (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.toList()

        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(syncAccountRepository).joinAccountFromThirdPartyRecoveryCode(captor.capture())
        val decodedJson = String(java.util.Base64.getUrlDecoder().decode(captor.firstValue), Charsets.UTF_8)
        assertFalse("re-wrapped 3party JSON must not contain escaped slashes (\\/): $decodedJson", decodedJson.contains("\\/"))
        val recovery = JSONObject(decodedJson).getJSONObject("recovery")
        assertEquals(secretWithSlashes, recovery.getString("secret"))
        assertEquals("u-3p", recovery.getString("user_id"))
        assertEquals("3party", recovery.getString("cid"))
        assertEquals("2.0", recovery.getString("v"))
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - ALREADY_SIGNED_IN triggers logoutAndJoinNewAccount and emits LoggedIn`() = runTest {
        setV2(true)
        val base64urlSecret = "rUzlGqLLlbonAC_zIeh1nrCmuDsDAn6UooUUDz-6x3o"
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", base64urlSecret)
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

        assertEquals(DispatchOutcome.LoggedIn(SetupPath.RECOVERY), outcome)

        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(syncAccountRepository).logoutAndJoinNewAccount(captor.capture())
        val decoded = java.util.Base64.getUrlDecoder().decode(captor.firstValue)
        val recovery = JSONObject(String(decoded)).getJSONObject("recovery")
        assertArrayEquals(
            java.util.Base64.getUrlDecoder().decode(base64urlSecret),
            java.util.Base64.getDecoder().decode(recovery.getString("primary_key")),
        )
        assertEquals("u-other", recovery.getString("user_id"))
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - account-switch code is canonical v1 JSON without escaped slashes`() = runTest {
        setV2(true)
        val secretWithSlashes = "apZ+7PAe89rDhuG4DRyi/M3zU2/D5DZNdRsR3RM6Ujw="
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", secretWithSlashes)
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

        (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        val captor = org.mockito.kotlin.argumentCaptor<String>()
        verify(syncAccountRepository).logoutAndJoinNewAccount(captor.capture())
        val decodedJson = String(java.util.Base64.getUrlDecoder().decode(captor.firstValue), Charsets.UTF_8)
        assertFalse("v1 account-switch JSON must not contain escaped slashes (\\/): $decodedJson", decodedJson.contains("\\/"))
        val recovery = JSONObject(decodedJson).getJSONObject("recovery")
        assertEquals(secretWithSlashes, recovery.getString("primary_key"))
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

        assertEquals(
            DispatchOutcome.Failed("Login failed", com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED.code, path = SetupPath.RECOVERY),
            outcome,
        )
    }

    @Test fun `FF on, v2 RecoveryCode cid=3party - ALREADY_SIGNED_IN does NOT trigger transparent switch (fails as Failed)`() = runTest {
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

        assertEquals(
            DispatchOutcome.Failed(
                "Already signed in",
                com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN.code,
                path = SetupPath.RECOVERY,
            ),
            outcome,
        )
        verify(syncAccountRepository, never()).logoutAndJoinNewAccount(any())
    }

    @Test fun `FF on, v2 RecoveryCode cid=ddg - non-ALREADY_SIGNED_IN error still becomes Failed`() = runTest {
        setV2(true)
        val rawJson = JSONObject().apply {
            put("user_id", "u")
            put("secret", "AQID") // valid base64 so decoding succeeds and processCode is reached
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(code = -1, reason = "Some other error"),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(DispatchOutcome.Failed("Some other error", path = SetupPath.RECOVERY), outcome)
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

        assertEquals(DispatchOutcome.Failed("BE rejected", path = SetupPath.RECOVERY), outcome)
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

    @Test fun `Legacy path - dispatcher never invokes processCode (caller owns that)`() = runTest {
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
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val staleJoinerDone = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event.Transition(
            timestampMs = 1L,
            from = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Joiner.Waiting,
            to = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State.Joiner.Done,
            trigger = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse(
                rawJson = "{}",
                recoveryCode = "stale-code-from-prior-session",
            ),
            localTrigger = null,
        )
        val staleFlow = MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 10)
        staleFlow.tryEmit(staleJoinerDone)
        whenever(runner.events).thenReturn(staleFlow)
        whenever(runner.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            staleFlow.filter { event -> event.timestampMs >= sinceMs }
        }

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        val outcome = kotlinx.coroutines.withTimeoutOrNull(100) { decision.outcomes.first() }
        assertEquals(null, outcome)
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `FF on, LinkingV2 - runner_startScan deferred until Flow is collected (cold)`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        verify(runner, never()).startScan(any())

        kotlinx.coroutines.withTimeoutOrNull(50) { decision.outcomes.first() }
        verify(runner).startScan(eq("v2-url"))
    }

    @Test fun `FF on, LinkingV2 exchange - base64url recovery secret is normalised to standard base64 for v1 login`() = runTest {
        // Spec 1214802412121967: the v2 wire `secret` is base64url; v1 login decodes as standard base64.
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))
        val base64urlSecret = "rUzlGqLLlbonAC_zIeh1nrCmuDsDAn6UooUUDz-6x3o"
        val payloadJson = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("user_id", "u-1")
                    put("secret", base64urlSecret)
                    put("cid", "ddg")
                    put("v", "2.0")
                },
            )
        }.toString()
        val recoveryCodeB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())

        val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { flow.take(1).toList() }
        runnerEventsFlow.emit(
            ExchangeV2Event.Transition(
                timestampMs = System.currentTimeMillis(),
                from = ExchangeV2State.Joiner.Waiting,
                to = ExchangeV2State.Joiner.Done,
                trigger = com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse(
                    rawJson = payloadJson,
                    recoveryCode = recoveryCodeB64,
                ),
                localTrigger = null,
            ),
        )
        job.join()

        val captor = org.mockito.kotlin.argumentCaptor<SyncAuthCode>()
        verify(syncAccountRepository).processCode(captor.capture(), anyOrNull())
        val primaryKey = (captor.firstValue as SyncAuthCode.Recovery).b64Code.primaryKey
        assertFalse("primaryKey must be standard base64 (no '-'): $primaryKey", primaryKey.contains('-'))
        assertFalse("primaryKey must be standard base64 (no '_'): $primaryKey", primaryKey.contains('_'))
        assertArrayEquals(
            java.util.Base64.getUrlDecoder().decode(base64urlSecret),
            java.util.Base64.getDecoder().decode(primaryKey),
        )
    }

    private fun transition(
        from: ExchangeV2State,
        to: ExchangeV2State,
        trigger: ExchangeV2Message? = null,
        localTrigger: LocalTrigger? = null,
        timestampMs: Long = System.currentTimeMillis(),
    ): ExchangeV2Event.Transition = ExchangeV2Event.Transition(
        timestampMs = timestampMs,
        from = from,
        to = to,
        trigger = trigger,
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
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(sessionStarted(linkingCode = null))
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Done))
        job.join()

        assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST), outcomes.single())
    }

    @Test fun `presentV2 emits HostConfirmationRequested with peerName when runner reaches Host_Confirming`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming))
        job.join()

        assertEquals(DispatchOutcome.HostConfirmationRequested(peerName = "Peer Phone"), outcomes.single())
    }

    @Test fun `presentV2 carries third-party peer kind on HostConfirmationRequested`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(runner.peerKind).thenReturn("3party")
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming))
        job.join()

        assertEquals(
            DispatchOutcome.HostConfirmationRequested(peerName = "Peer Phone", peerKind = PeerKind.THIRD_PARTY),
            outcomes.single(),
        )
    }

    @Test fun `presentV2 carries ddg peer kind on JoinerConfirmationRequested`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(runner.peerKind).thenReturn("ddg")
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().take(1).collect { outcomes += it } }
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Joiner.Confirming))
        job.join()

        assertEquals(
            DispatchOutcome.JoinerConfirmationRequested(peerName = "Peer Phone", peerKind = PeerKind.DDG),
            outcomes.single(),
        )
    }

    @Test fun `presentV2 emits LoggedIn with Host role and peer kind when runner reaches Host_Done`() = runTest {
        whenever(runner.peerKind).thenReturn("3party")
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it is DispatchOutcome.LoggedIn || it is DispatchOutcome.Failed }
            }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Sending, to = ExchangeV2State.Host.Done))
            job.await()
        }
        assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST, PeerKind.THIRD_PARTY), outcome)
    }

    @Test fun `presentV2 maps a version-too-new SessionError to UpgradeRequired`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(
                    timestampMs = System.currentTimeMillis(),
                    message = "Peer requires protocol v3; please update this app",
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING), outcome)
    }

    @Test fun `route LinkingV2 maps a version-too-new SessionError to UpgradeRequired`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val outcome = withTimeoutOrNull(1000) {
            val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { flow.first() }
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(
                    timestampMs = System.currentTimeMillis(),
                    message = "Peer requires protocol v3; please update this app",
                ),
            )
            job.await()
        }
        assertEquals(DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING), outcome)
    }

    @Test fun `route LinkingV2 - Host_Aborted with UserDeniedHost maps to Failed PAIRING_CANCELLED`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val outcome = withTimeoutOrNull(1000) {
            val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { flow.first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.UserDeniedHost,
                ),
            )
            job.await()
        }
        assertEquals(
            DispatchOutcome.Failed("user_denied", PAIRING_CANCELLED.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
            outcome,
        )
    }

    @Test fun `route LinkingV2 - Host_Aborted with HostUnavailable maps to Failed PAIRING_UNAVAILABLE`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val outcome = withTimeoutOrNull(1000) {
            val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { flow.first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Sending,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.HostUnavailable,
                ),
            )
            job.await()
        }
        assertEquals(
            DispatchOutcome.Failed("host_unavailable", PAIRING_UNAVAILABLE.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
            outcome,
        )
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
        assertEquals(
            DispatchOutcome.Failed("user_denied", PAIRING_CANCELLED.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
            outcome,
        )
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
        assertEquals(
            DispatchOutcome.Failed("host_unavailable", PAIRING_UNAVAILABLE.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
            outcome,
        )
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
        assertEquals(DispatchOutcome.Failed("channel 5xx", PAIRING_FAILED.code, path = SetupPath.PAIRING), outcome)
    }

    @Test fun `presentV2 emits Failed when Joiner_Done arrives without a recovery code`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                dispatcher.presentV2().first { it !is DispatchOutcome.LinkingCodeReady }
            }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.Done))
            job.await()
        }
        assertEquals(
            DispatchOutcome.Failed(
                "Pairing completed without a recovery code",
                NO_RECOVERY_CODE.code,
                path = SetupPath.PAIRING,
                myRole = SetupRole.JOINER,
            ),
            outcome,
        )
    }

    @Test fun `presentV2 emits LoggedIn when Joiner_Done carries a cid=ddg recovery code`() = runTest {
        setV2(true)
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
        whenever(runner.peerKind).thenReturn("ddg")

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
        assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.JOINER, PeerKind.DDG), outcome)
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
        assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.JOINER), outcome)
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
        assertEquals(
            DispatchOutcome.Failed(
                "Pairing cancelled on this device",
                PAIRING_CANCELLED.code,
                path = SetupPath.PAIRING,
                myRole = SetupRole.JOINER,
            ),
            outcome,
        )
    }

    @Test fun `presentV2 calls runner_startPresent when Flow is collected`() = runTest {
        val flow = dispatcher.presentV2()
        verify(runner, never()).startPresent()

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
        runnerEventsFlow.emit(sessionStarted(linkingCode = "code-for-own-channel"))
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Scanner,
                ownChannelId = "other-channel",
                linkingCode = null,
            ),
        )
        job.join()

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
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Scanner,
                ownChannelId = "scanner-own-channel",
                linkingCode = null,
            ),
        )
        runnerEventsFlow.emit(
            ExchangeV2Event.SessionStarted(
                timestampMs = System.currentTimeMillis(),
                pairingRole = PairingRole.Presenter,
                ownChannelId = "different-channel",
                linkingCode = "code",
            ),
        )
        job.join()

        assertTrue("expected no outcomes, got $outcomes", outcomes.isEmpty())
    }

    @Test fun `presentV2 emits Failed when runner reaches Aborted (hello during negotiating)`() = runTest {
        val outcomes = mutableListOf<DispatchOutcome>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            dispatcher.presentV2().take(1).collect { outcomes += it }
        }
        runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
        job.join()

        assertTrue("expected Failed, got ${outcomes.single()}", outcomes.single() is DispatchOutcome.Failed)
    }

    @Test fun `linking flow emits Failed when runner reaches Aborted (hello during negotiating)`() = runTest {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                decision.outcomes.first { it is DispatchOutcome.Failed }
            }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
            job.await()
        }
        assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
    }

    @Test fun `presentV2 filters out events from before session start`() = runTest {
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

        val outcome = withTimeoutOrNull(100) { dispatcher.presentV2().first() }
        assertEquals(null, outcome)
    }

    private fun startLinking(): kotlinx.coroutines.flow.Flow<DispatchOutcome> {
        setV2(true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        return (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
    }

    @Test fun `linking - AbortedByHost with RecoveryCodeDenied maps to Failed PAIRING_REJECTED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeDenied(rawJson = "{}"),
                ),
            )
            job.await()
        }
        assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
        assertEquals(PAIRING_REJECTED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - AbortedByHost with RecoveryCodeUnavailable maps to Failed PAIRING_UNAVAILABLE`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeUnavailable(rawJson = "{}"),
                ),
            )
            job.await()
        }
        assertEquals(PAIRING_UNAVAILABLE.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - Joiner AbortedLocal maps to Failed PAIRING_CANCELLED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Confirming, to = ExchangeV2State.Joiner.AbortedLocal))
            job.await()
        }
        assertEquals(PAIRING_CANCELLED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - Aborted maps to Failed NEGOTIATION_ABORTED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
            job.await()
        }
        assertEquals(NEGOTIATION_ABORTED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - Joiner Done without recovery code maps to Failed NO_RECOVERY_CODE`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.Done))
            job.await()
        }
        assertEquals(NO_RECOVERY_CODE.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - SessionError maps to Failed PAIRING_FAILED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(ExchangeV2Event.SessionError(timestampMs = System.currentTimeMillis(), message = "channel 500"))
            job.await()
        }
        assertEquals(PAIRING_FAILED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `linking - Host Aborted maps to Failed NEGOTIATION_ABORTED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { startLinking().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Confirming, to = ExchangeV2State.Host.Aborted))
            job.await()
        }
        assertEquals(NEGOTIATION_ABORTED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `present - AbortedByHost with RecoveryCodeDenied maps to Failed PAIRING_REJECTED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeDenied(rawJson = "{}"),
                ),
            )
            job.await()
        }
        assertEquals(PAIRING_REJECTED.code, (outcome as DispatchOutcome.Failed).code)
    }

    @Test fun `present - Host Aborted with no localTrigger maps to Failed NEGOTIATION_ABORTED`() = runTest {
        val outcome = withTimeoutOrNull(1000) {
            val job = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { dispatcher.presentV2().first() }
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Confirming, to = ExchangeV2State.Host.Aborted))
            job.await()
        }
        assertEquals(NEGOTIATION_ABORTED.code, (outcome as DispatchOutcome.Failed).code)
    }
}
