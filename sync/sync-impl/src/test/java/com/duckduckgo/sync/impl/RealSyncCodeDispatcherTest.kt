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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.NEGOTIATION_ABORTED
import com.duckduckgo.sync.impl.AccountErrorCodes.NO_RECOVERY_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_UNAVAILABLE
import com.duckduckgo.sync.impl.AccountErrorCodes.UNEXPECTED_EVENT
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealSyncCodeDispatcherTest {

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val qrCode: ExchangeV2QrCode = mock()

    private val runnerEventsFlow = MutableSharedFlow<ExchangeV2Event>(replay = 0)
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

    private fun configureFeatureFlag(canUseV2Code: Boolean) {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(Toggle.State(enable = canUseV2Code))
    }

    @Test fun `v2 flag off - returns Legacy with whatever parseSyncAuthCode returned, and never touches qrCode parse`() {
        configureFeatureFlag(canUseV2Code = false)
        val expectedAuthCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "pk", userId = "u"))
        whenever(syncAccountRepository.parseSyncAuthCode("the-code")).thenReturn(expectedAuthCode)

        val decision = dispatcher.route("the-code") as RouteDecision.Legacy

        assertSame(expectedAuthCode, decision.authCode)
        verify(qrCode, never()).parse(any())
    }

    @Test fun `v2 flag off - v1 Connect codes return Legacy(Connect) unchanged`() {
        configureFeatureFlag(canUseV2Code = false)
        val connect = SyncAuthCode.Connect(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(connect)

        val decision = dispatcher.route("connect-shape-code") as RouteDecision.Legacy

        assertSame(connect, decision.authCode)
    }

    @Test fun `v2 flag off - v1 Exchange codes return Legacy(Exchange) unchanged`() {
        configureFeatureFlag(canUseV2Code = false)
        val exchange = SyncAuthCode.Exchange(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(exchange)

        val decision = dispatcher.route("exchange-shape-code") as RouteDecision.Legacy

        assertSame(exchange, decision.authCode)
    }

    @Test fun `v2 flag off - Unknown codes still bubble back as Legacy(Unknown)`() {
        configureFeatureFlag(canUseV2Code = false)
        val unknown = SyncAuthCode.Unknown("garbage")
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(unknown)

        val decision = dispatcher.route("garbage") as RouteDecision.Legacy

        assertSame(unknown, decision.authCode)
    }

    @Test fun `v2 flag off - even a v2-shaped code is routed via legacy parseSyncAuthCode (no v2 parser touched)`() {
        configureFeatureFlag(canUseV2Code = false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("v2-shaped-input"))

        dispatcher.route("https://duckduckgo.com/sync/pairing/#&code2=anything")

        verify(qrCode, never()).parse(any())
        verify(syncAccountRepository).parseSyncAuthCode("https://duckduckgo.com/sync/pairing/#&code2=anything")
    }

    @Test fun `v2 flag on but v2 parser returns LinkingV1 - falls back to legacy stack`() {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.LinkingV1)
        val connect = SyncAuthCode.Connect(mock())
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(connect)

        val decision = dispatcher.route("v1-code") as RouteDecision.Legacy

        assertSame(connect, decision.authCode)
    }

    @Test fun `v2 flag on but v2 parser returns Unknown - falls back to legacy stack`() {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.Unknown)
        val recovery = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "pk", userId = "u"))
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(recovery)

        val decision = dispatcher.route("recovery-code") as RouteDecision.Legacy

        assertSame(recovery, decision.authCode)
    }

    @Test fun `v2 flag on, v2 LinkingV2 - returns V2InProgress and does NOT call parseSyncAuthCode`() {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - flow emits LoggedIn after processCode succeeds`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - base64url secret is normalised to standard base64 for v1 login`() = runTest {
        // Spec 1214802412121967: the v2 wire `secret` is base64url; v1 login decodes as standard base64.
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg with missing user_id - emits Failed without calling processCode`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=3party - flow calls joinAccountFromThirdPartyRecoveryCode`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=3party - re-encoded code is canonical JSON without escaped slashes`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - ALREADY_SIGNED_IN triggers logoutAndJoinNewAccount and emits LoggedIn`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        val base64urlSecret = "rUzlGqLLlbonAC_zIeh1nrCmuDsDAn6UooUUDz-6x3o"
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", base64urlSecret)
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(
                code = ALREADY_SIGNED_IN.code,
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - account-switch code is canonical v1 JSON without escaped slashes`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        val secretWithSlashes = "apZ+7PAe89rDhuG4DRyi/M3zU2/D5DZNdRsR3RM6Ujw="
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", secretWithSlashes)
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(
                code = ALREADY_SIGNED_IN.code,
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - ALREADY_SIGNED_IN then logoutAndJoinNewAccount failure surfaces as Failed`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-other")
            put("secret", "s-other")
            put("cid", "ddg")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(
            Result.Error(
                code = ALREADY_SIGNED_IN.code,
                reason = "Already signed in",
            ),
        )
        whenever(syncAccountRepository.logoutAndJoinNewAccount(any())).thenReturn(
            Result.Error(code = LOGIN_FAILED.code, reason = "Login failed"),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(
            DispatchOutcome.Failed("Login failed", LOGIN_FAILED.code, path = SetupPath.RECOVERY),
            outcome,
        )
    }

    @Test fun `v2 flag on, v2 RecoveryCode cid=3party - ALREADY_SIGNED_IN does NOT trigger transparent switch (fails as Failed)`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        val rawJson = JSONObject().apply {
            put("user_id", "u-3p")
            put("secret", "s-3p")
            put("cid", "3party")
        }
        whenever(qrCode.parse(any())).thenReturn(ExchangeV2CodeParseResult.RecoveryCode(rawJson))
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(
            Result.Error(
                code = ALREADY_SIGNED_IN.code,
                reason = "Already signed in",
            ),
        )

        val outcome = (dispatcher.route("any") as RouteDecision.V2InProgress).outcomes.first()

        assertEquals(
            DispatchOutcome.Failed(
                "Already signed in",
                ALREADY_SIGNED_IN.code,
                path = SetupPath.RECOVERY,
            ),
            outcome,
        )
        verify(syncAccountRepository, never()).logoutAndJoinNewAccount(any())
    }

    @Test fun `v2 flag on, v2 RecoveryCode cid=ddg - non-ALREADY_SIGNED_IN error still becomes Failed`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode cid=3party - repository error becomes Failed with reason preserved`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `v2 flag on, v2 RecoveryCode with unknown cid - emits Failed with the cid in the reason`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
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

    @Test fun `Legacy path - dispatcher never invokes processCode (caller owns that)`() {
        configureFeatureFlag(canUseV2Code = false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Recovery(mock()))

        dispatcher.route("any")

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
        verify(syncAccountRepository, never()).joinAccountFromThirdPartyRecoveryCode(any())
    }

    @Test fun `Legacy path - dispatcher does not start the v2 runner`() {
        configureFeatureFlag(canUseV2Code = false)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("x"))

        dispatcher.route("any")

        verify(runner, never()).startScan(any())
    }

    @Test fun `v2 flag on, LinkingV2 - ignores stale terminal events from prior sessions in the replay cache`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val staleJoinerDone = ExchangeV2Event.Transition(
            timestampMs = 1L,
            from = ExchangeV2State.Joiner.Waiting,
            to = ExchangeV2State.Joiner.Done,
            trigger = ExchangeV2Message.RecoveryCodeResponse(
                rawJson = "{}",
                recoveryCode = "stale-code-from-prior-session",
            ),
            localTrigger = null,
        )
        val staleFlow = MutableSharedFlow<ExchangeV2Event>(replay = 10)
        staleFlow.tryEmit(staleJoinerDone)
        whenever(runner.events).thenReturn(staleFlow)
        whenever(runner.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            staleFlow.filter { event -> event.timestampMs >= sinceMs }
        }

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        decision.outcomes.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `v2 flag on, LinkingV2 - runner_startScan deferred until Flow is collected (cold)`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        verify(runner, never()).startScan(any())

        decision.outcomes.test { cancelAndIgnoreRemainingEvents() }
        verify(runner).startScan(eq("v2-url"))
    }

    @Test fun `v2 flag on, LinkingV2 exchange - base64url recovery secret is normalised to standard base64 for v1 login`() = runTest {
        // Spec 1214802412121967: the v2 wire `secret` is base64url; v1 login decodes as standard base64.
        configureFeatureFlag(canUseV2Code = true)
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
        flow.test {
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = ExchangeV2Message.RecoveryCodeResponse(
                        rawJson = payloadJson,
                        recoveryCode = recoveryCodeB64,
                    ),
                    localTrigger = null,
                ),
            )
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

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

    @Test fun `Presenter emits LinkingCodeReady when runner emits SessionStarted with a linkingCode`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(sessionStarted(linkingCode = "https://duckduckgo.com/sync/pairing?code2=abc"))
            assertEquals(
                DispatchOutcome.LinkingCodeReady("https://duckduckgo.com/sync/pairing?code2=abc"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter ignores SessionStarted with null linkingCode (Scanner side leakage protection)`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(sessionStarted(linkingCode = null))
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Done))
            assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits HostConfirmationRequested with peerName when runner reaches Host_Confirming`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming))
            assertEquals(DispatchOutcome.HostConfirmationRequested(peerName = "Peer Phone"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter carries third-party peer kind on HostConfirmationRequested`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(runner.peerKind).thenReturn("3party")
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming))
            assertEquals(
                DispatchOutcome.HostConfirmationRequested(peerName = "Peer Phone", peerKind = PeerKind.THIRD_PARTY),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter carries ddg peer kind on JoinerConfirmationRequested`() = runTest {
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(runner.peerKind).thenReturn("ddg")
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Joiner.Confirming))
            assertEquals(
                DispatchOutcome.JoinerConfirmationRequested(peerName = "Peer Phone", peerKind = PeerKind.DDG),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits LoggedIn with Host role and peer kind when runner reaches Host_Done`() = runTest {
        whenever(runner.peerKind).thenReturn("3party")
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Sending, to = ExchangeV2State.Host.Done))
            assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.HOST, PeerKind.THIRD_PARTY), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter maps a version-too-new SessionError to UpgradeRequired`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(
                    timestampMs = System.currentTimeMillis(),
                    message = "Peer requires protocol v3; please update this app",
                ),
            )
            assertEquals(DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner maps a version-too-new SessionError to UpgradeRequired`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
        flow.test {
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(
                    timestampMs = System.currentTimeMillis(),
                    message = "Peer requires protocol v3; please update this app",
                ),
            )
            assertEquals(DispatchOutcome.UpgradeRequired(codeMajor = 3, path = SetupPath.PAIRING), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Host_Aborted with UserDeniedHost maps to Failed PAIRING_CANCELLED`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
        flow.test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.UserDeniedHost,
                ),
            )
            assertEquals(
                DispatchOutcome.Failed("user_denied", PAIRING_CANCELLED.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Host_Aborted with HostUnavailable maps to Failed PAIRING_UNAVAILABLE`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val flow = (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
        flow.test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Sending,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.HostUnavailable,
                ),
            )
            assertEquals(
                DispatchOutcome.Failed("host_unavailable", PAIRING_UNAVAILABLE.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed user_denied when Host_Aborted carries UserDeniedHost trigger`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.UserDeniedHost,
                ),
            )
            assertEquals(
                DispatchOutcome.Failed("user_denied", PAIRING_CANCELLED.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed host_unavailable when Host_Aborted carries HostUnavailable trigger`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Sending,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.HostUnavailable,
                ),
            )
            assertEquals(
                DispatchOutcome.Failed("host_unavailable", PAIRING_UNAVAILABLE.code, path = SetupPath.PAIRING, myRole = SetupRole.HOST),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits AlreadyConnected when runner reaches SameAccountAbort`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.SameAccountAbort))
            assertEquals(DispatchOutcome.AlreadyConnected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed when runner emits SessionError`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(timestampMs = System.currentTimeMillis(), message = "channel 5xx"),
            )
            assertEquals(DispatchOutcome.Failed("channel 5xx", PAIRING_FAILED.code, path = SetupPath.PAIRING), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed when Joiner_Done arrives without a recovery code`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.Done))
            assertEquals(
                DispatchOutcome.Failed(
                    "joiner_done_missing_recovery_code",
                    NO_RECOVERY_CODE.code,
                    path = SetupPath.PAIRING,
                    myRole = SetupRole.JOINER,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits LoggedIn when Joiner_Done carries a cid=ddg recovery code`() = runTest {
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
        val responseMessage = ExchangeV2Message.RecoveryCodeResponse(
            rawJson = "{}",
            recoveryCode = b64,
        )
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))
        whenever(runner.peerKind).thenReturn("ddg")

        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = responseMessage,
                    localTrigger = null,
                ),
            )
            assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.JOINER, PeerKind.DDG), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncAccountRepository).processCode(any(), anyOrNull())
        verify(syncAccountRepository, never()).joinAccountFromThirdPartyRecoveryCode(any())
    }

    @Test fun `Presenter emits LoggedIn via 3party upgrade when Joiner_Done carries a cid=3party recovery code`() = runTest {
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
        val responseMessage = ExchangeV2Message.RecoveryCodeResponse(
            rawJson = "{}",
            recoveryCode = b64,
        )
        whenever(syncAccountRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(Result.Success(true))

        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = responseMessage,
                    localTrigger = null,
                ),
            )
            assertEquals(DispatchOutcome.LoggedIn(SetupPath.PAIRING, SetupRole.JOINER), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncAccountRepository).joinAccountFromThirdPartyRecoveryCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `Presenter emits Failed PAIRING_CANCELLED when Joiner_AbortedLocal driven by UserDeniedJoiner`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    localTrigger = LocalTrigger.UserDeniedJoiner,
                ),
            )
            assertEquals(
                DispatchOutcome.Failed(
                    "user_denied_joiner",
                    PAIRING_CANCELLED.code,
                    path = SetupPath.PAIRING,
                    myRole = SetupRole.JOINER,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed UNEXPECTED_EVENT when Joiner_AbortedLocal driven by wire message`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    trigger = ExchangeV2Message.Hello(rawJson = "{}"),
                ),
            )
            assertEquals(UNEXPECTED_EVENT.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed UNEXPECTED_EVENT when Host_Aborted driven by wire message`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    trigger = ExchangeV2Message.RecoveryCodeResponse(rawJson = "{}"),
                ),
            )
            assertEquals(UNEXPECTED_EVENT.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter calls runner_startPresent when Flow is collected`() = runTest {
        val flow = dispatcher.presentV2()
        verify(runner, never()).startPresent()

        flow.test { cancelAndIgnoreRemainingEvents() }
        verify(runner).startPresent()
    }

    @Test fun `Presenter cancels runner when collecting coroutine is cancelled`() = runTest {
        val flow = dispatcher.presentV2()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            flow.collect { /* no-op */ }
        }
        verify(runner).startPresent()

        job.cancel()
        job.join()

        verify(runner).cancel()
    }

    @Test fun `Scanner cancels runner when collecting coroutine is cancelled`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(
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

    @Test fun `Presenter terminates silently when another SessionStarted with different channel arrives`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(sessionStarted(linkingCode = "code-for-own-channel"))
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionStarted(
                    timestampMs = System.currentTimeMillis(),
                    pairingRole = PairingRole.Scanner,
                    ownChannelId = "other-channel",
                    linkingCode = null,
                ),
            )
            assertEquals(DispatchOutcome.LinkingCodeReady("code-for-own-channel"), awaitItem())
            awaitComplete()
        }
    }

    @Test fun `Scanner terminates silently when another SessionStarted with different channel arrives`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(
                channelId = "peer-channel",
                publicKey = "k",
                version = "2",
            ),
        )
        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress

        decision.outcomes.test {
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
            awaitComplete()
        }
    }

    @Test fun `Presenter emits Failed when runner reaches Aborted (hello during negotiating)`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
            val outcome = awaitItem()
            assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner emits Failed when runner reaches Aborted (hello during negotiating)`() = runTest {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        val decision = dispatcher.route("v2-url") as RouteDecision.V2InProgress
        decision.outcomes.test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
            val outcome = awaitItem()
            assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter filters out events from before session start`() = runTest {
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

        dispatcher.presentV2().test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun startLinking(): kotlinx.coroutines.flow.Flow<DispatchOutcome> {
        configureFeatureFlag(canUseV2Code = true)
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        return (dispatcher.route("v2-url") as RouteDecision.V2InProgress).outcomes
    }

    @Test fun `Scanner - Joiner_AbortedByHost with RecoveryCodeDenied maps to Failed PAIRING_REJECTED`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeDenied(rawJson = "{}"),
                ),
            )
            val outcome = awaitItem()
            assertTrue("expected Failed, got $outcome", outcome is DispatchOutcome.Failed)
            assertEquals(PAIRING_REJECTED.code, (outcome as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_AbortedByHost with RecoveryCodeUnavailable maps to Failed PAIRING_UNAVAILABLE`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeUnavailable(rawJson = "{}"),
                ),
            )
            assertEquals(PAIRING_UNAVAILABLE.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_AbortedLocal with UserDeniedJoiner maps to Failed PAIRING_CANCELLED`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    localTrigger = LocalTrigger.UserDeniedJoiner,
                ),
            )
            assertEquals(PAIRING_CANCELLED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_AbortedLocal driven by wire message maps to Failed UNEXPECTED_EVENT`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    trigger = ExchangeV2Message.Hello(rawJson = "{}"),
                ),
            )
            assertEquals(UNEXPECTED_EVENT.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Host_Aborted driven by wire message maps to Failed UNEXPECTED_EVENT`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    trigger = ExchangeV2Message.Hello(rawJson = "{}"),
                ),
            )
            assertEquals(UNEXPECTED_EVENT.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Aborted maps to Failed NEGOTIATION_ABORTED`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Aborted))
            assertEquals(NEGOTIATION_ABORTED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_Done without recovery code maps to Failed NO_RECOVERY_CODE`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.Done))
            assertEquals(NO_RECOVERY_CODE.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - SessionError maps to Failed PAIRING_FAILED`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(ExchangeV2Event.SessionError(timestampMs = System.currentTimeMillis(), message = "channel 500"))
            assertEquals(PAIRING_FAILED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Host_Aborted maps to Failed NEGOTIATION_ABORTED`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Confirming, to = ExchangeV2State.Host.Aborted))
            assertEquals(NEGOTIATION_ABORTED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter - Joiner_AbortedByHost with RecoveryCodeDenied maps to Failed PAIRING_REJECTED`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeDenied(rawJson = "{}"),
                ),
            )
            assertEquals(PAIRING_REJECTED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter - Host_Aborted with no localTrigger maps to Failed NEGOTIATION_ABORTED`() = runTest {
        dispatcher.presentV2().test {
            runnerEventsFlow.emit(transition(from = ExchangeV2State.Host.Confirming, to = ExchangeV2State.Host.Aborted))
            assertEquals(NEGOTIATION_ABORTED.code, (awaitItem() as DispatchOutcome.Failed).code)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_AbortedByHost with an unrecognised trigger maps to Failed PAIRING_REJECTED (peer_aborted)`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.Hello(rawJson = "{}"),
                ),
            )
            val outcome = awaitItem() as DispatchOutcome.Failed
            assertEquals(PAIRING_REJECTED.code, outcome.code)
            assertEquals("peer_aborted", outcome.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Scanner - Joiner_AbortedLocal with no trigger maps to Failed PAIRING_FAILED (joiner_local_aborted)`() = runTest {
        startLinking().test {
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Joiner.Confirming, to = ExchangeV2State.Joiner.AbortedLocal),
            )
            val outcome = awaitItem() as DispatchOutcome.Failed
            assertEquals(PAIRING_FAILED.code, outcome.code)
            assertEquals("joiner_local_aborted", outcome.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Presenter emits Failed when Joiner_Done carries a recovery code with unknown cid`() = runTest {
        val recoveryJson = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("user_id", "u-1")
                    put("secret", "s-1")
                    put("cid", "future-credential")
                    put("v", "2.0")
                },
            )
        }.toString()
        val b64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        val responseMessage = ExchangeV2Message.RecoveryCodeResponse(rawJson = "{}", recoveryCode = b64)

        dispatcher.presentV2().test {
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = responseMessage,
                    localTrigger = null,
                ),
            )
            val outcome = awaitItem() as DispatchOutcome.Failed
            assertTrue("expected reason to mention the unknown cid, got '${outcome.reason}'", outcome.reason.contains("future-credential"))
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
        verify(syncAccountRepository, never()).joinAccountFromThirdPartyRecoveryCode(any())
    }
}
