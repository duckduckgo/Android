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

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncDeviceIds
import com.duckduckgo.sync.impl.crypto.RsaKeyPair
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Hello
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeRequest
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealExchangeV2RunnerTest {

    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val clock = ExchangeV2Clock { 42L }
    private val parser = JsonExchangeV2MessageParser()
    private val factory = ExchangeV2StateMachineFactory(clock)
    private val syncStore: SyncStore = mock()
    private val jweCrypto: SyncJweCrypto = mock()
    private val channel: ExchangeV2Channel = mock()
    private val qrCode: ExchangeV2QrCode = mock()
    private val recoveryCodeProvider: RecoveryCodeProvider = mock()
    private val syncDeviceIds: SyncDeviceIds = mock()

    private fun newRunner(): RealExchangeV2Runner =
        RealExchangeV2Runner(
            smFactory = factory,
            messageParser = parser,
            clock = clock,
            syncStore = syncStore,
            jweCrypto = jweCrypto,
            channel = channel,
            qrCode = qrCode,
            recoveryCodeProvider = recoveryCodeProvider,
            syncDeviceIds = syncDeviceIds,
            appScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

    @Before fun stubWireDeps() {
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "peer-channel", publicKey = "peer-pubkey", version = "2"),
        )
        whenever(qrCode.buildLinkingCode(any(), any(), any())).thenReturn("https://duckduckgo.com/sync/pairing/#&code2=fake")
        whenever(jweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair(publicKeyBase64 = "own-pub", privateKeyBase64 = "own-priv"))
        whenever(channel.createChannel(any())).thenReturn(Result.Success(Unit))
        whenever(channel.poll(any(), any())).thenReturn(emptyFlow())
        whenever(channel.sendMessage(any(), any(), any(), any())).thenReturn(Result.Success(Unit))
        whenever(channel.deleteChannel(any())).thenReturn(Result.Success(Unit))
        whenever(syncDeviceIds.deviceName()).thenReturn("This Device")
    }

    @Test fun `startScan creates a session in Negotiating (Scanner already knows the peer)`() {
        val runner = newRunner()
        runner.startScan(pastedUrl = "ignored")

        assertSame(ExchangeV2State.Negotiating, runner.currentState)
    }

    @Test fun `cancel abandons the session`() = runTest {
        val runner = newRunner()
        runner.startScan("")
        runner.cancel()

        assertNull(runner.currentState)
    }

    @Test fun `startScan called twice replaces the existing session`() = runTest {
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer", name = "Peer", kind = "3party"),
        )

        runner.startScan("")

        assertSame(ExchangeV2State.Negotiating, runner.currentState)
        assertSame(PairingRole.Scanner, runner.pairingRole)
    }

    @Test fun `deliverIncomingMessage forwards SM event to flow`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            runner.deliverIncomingMessage(Hello("""{"type":"hello"}"""))
            val event = awaitItem()
            assertSame(ExchangeV2State.Bootstrapped, event.from)
            assertSame(ExchangeV2State.Negotiating, event.to)
        }
    }

    @Test fun `deliverIncomingMessage without a session is a no-op`() = runTest {
        val runner = newRunner()
        runner.events.test {
            runner.deliverIncomingMessage(Hello("{}"))
            expectNoEvents()
        }
    }

    @Test fun `deliverIncomingMessageJson parses and forwards`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            runner.deliverIncomingMessageJson("""{"type":"hello"}""")
            val event = awaitItem()
            assertSame(ExchangeV2State.Negotiating, event.to)
            assertTrue(event.trigger is Hello)
        }
    }

    @Test fun `deliverIncomingMessageJson with unknown type emits dropped`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()

        runner.events.filterIsInstance<ExchangeV2Event.MessageRejected>().test {
            runner.deliverIncomingMessageJson("""{"type":"future_msg"}""")
            val event = awaitItem()
            assertSame(RejectReason.UnknownMessageDropped, event.reason)
        }
    }

    @Test fun `recordSentMessage emits MessageSent with given message`() = runTest {
        val runner = newRunner()
        val sent = RecoveryCodeRequest(rawJson = "{}", name = "me", kind = "3party")

        runner.events.test {
            runner.recordSentMessage(sent)
            val event = awaitItem() as ExchangeV2Event.MessageSent
            assertSame(sent, event.message)
        }
    }

    @Test fun `localTrigger forwards SM event to flow`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            awaitItem()
            runner.localTrigger(LocalTrigger.RoleElected(Role.Host))
            val event = awaitItem()
            assertSame(ExchangeV2State.Host.Confirming, event.to)
        }
    }

    @Test fun `terminal state abandons session`() = runTest {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "other", name = "Peer", kind = "3party"))
        runner.localTrigger(LocalTrigger.RoleElected(Role.Joiner))
        runner.localTrigger(LocalTrigger.UserConfirmedJoiner)
        runner.deliverIncomingMessage(RecoveryCodeResponse("{}"))

        assertNull(runner.currentState)
    }

    @Test fun `startScan passes local user_id from SyncStore so same-account abort fires`() = runTest {
        whenever(syncStore.userId).thenReturn("shared-user")
        val runner = newRunner()
        runner.startScan("")

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            runner.deliverIncomingMessage(
                ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "shared-user", name = "Peer", kind = "3party"),
            )
            val event = awaitItem()
            assertSame(ExchangeV2State.SameAccountAbort, event.to)
        }
    }

    @Test fun `availability without an account carries this device's real name, not a hardcoded platform string`() = runTest {
        whenever(syncStore.userId).thenReturn(null)
        whenever(syncDeviceIds.deviceName()).thenReturn("google Pixel 7")
        val runner = newRunner()

        runner.startScan("")

        // The peer shows this name on its security-confirmation screen, so it must be the real
        // device name (e.g. "google Pixel 7"), never a generic "Android".
        verify(channel).sendMessage(
            argThat { contains("recovery_code_request") && contains("\"name\":\"google Pixel 7\"") },
            any(),
            any(),
            any(),
        )
    }

    @Test fun `availability with an account carries this device's real name, not a hardcoded platform string`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(syncDeviceIds.deviceName()).thenReturn("google Pixel 7")
        val runner = newRunner()

        runner.startScan("")

        verify(channel).sendMessage(
            argThat { contains("recovery_code_available") && contains("\"name\":\"google Pixel 7\"") },
            any(),
            any(),
            any(),
        )
    }

    // ---- Auto role election ----

    @Test fun `startScan sets pairingRole to Scanner`() {
        val runner = newRunner()
        runner.startScan("")

        assertSame(PairingRole.Scanner, runner.pairingRole)
    }

    @Test fun `startPresent sets pairingRole to Presenter and bootstraps session`() {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()

        assertSame(PairingRole.Presenter, runner.pairingRole)
        assertSame(ExchangeV2State.Bootstrapped, runner.currentState)
    }

    @Test fun `startPresent without an account still bootstraps (spec allows account creation during pairing)`() {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startPresent()

        // Spec §"Exchange Share Recovery Code": Host may create its account during pairing.
        assertSame(PairingRole.Presenter, runner.pairingRole)
        assertSame(ExchangeV2State.Bootstrapped, runner.currentState)
    }

    @Test fun `canStartAsPresenter is true regardless of account state`() {
        whenever(syncStore.userId).thenReturn(null)
        assertTrue(newRunner().canStartAsPresenter)
        whenever(syncStore.userId).thenReturn("my-user")
        assertTrue(newRunner().canStartAsPresenter)
    }

    @Test fun `cancel clears pairingRole`() = runTest {
        val runner = newRunner()
        runner.startScan("")
        runner.cancel()

        assertNull(runner.pairingRole)
    }

    @Test fun `Scanner with no account auto-elects Joiner when peer (ddg) has account`() = runTest {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startScan("")

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "host-user", name = "Host", kind = "ddg"),
        )

        assertSame(ExchangeV2State.Joiner.Confirming, runner.currentState)
    }

    @Test fun `Presenter with account auto-elects Host when peer has no account`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeRequest(rawJson = "{}", name = "Joiner", kind = "ddg"),
        )

        assertSame(ExchangeV2State.Host.Confirming, runner.currentState)
    }

    @Test fun `entering Host_Confirming sends recovery_code_awaiting_confirmation immediately (spec ordering)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeRequest(rawJson = "{}", name = "Joiner", kind = "ddg"),
        )

        // Spec §"Exchange Confirmations → Host": awaiting_confirmation is sent on entry to
        // Confirming, before the user is prompted.
        verify(channel).sendMessage(
            argThat { contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
        verify(channel, never()).sendMessage(
            argThat { contains("recovery_code_confirmed") },
            any(),
            any(),
            any(),
        )
    }

    @Test fun `UserConfirmedHost sends recovery_code_confirmed but does NOT re-send awaiting_confirmation`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(recoveryCodeProvider.getDdgRecoveryCode()).thenReturn(com.duckduckgo.sync.impl.Result.Success("the-code"))
        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))
        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeRequest(rawJson = "{}", name = "Joiner", kind = "ddg"),
        )
        runner.localTrigger(LocalTrigger.UserConfirmedHost)

        verify(channel).sendMessage(
            argThat { contains("recovery_code_confirmed") && !contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
        verify(channel, org.mockito.kotlin.times(1)).sendMessage(
            argThat { contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
    }

    @Test fun `Host aborts (not Done) when the recovery_code_response send fails`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(recoveryCodeProvider.createDdgAccountIfNeeded()).thenReturn(Result.Success(Unit))
        whenever(recoveryCodeProvider.getDdgRecoveryCode()).thenReturn(Result.Success("the-code"))
        whenever(
            channel.sendMessage(argThat { contains("recovery_code_response") }, any(), any(), any()),
        ).thenReturn(Result.Error(reason = "relay unreachable"))

        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))
        runner.deliverIncomingMessage(RecoveryCodeRequest(rawJson = "{}", name = "Joiner", kind = "ddg"))
        runner.localTrigger(LocalTrigger.UserConfirmedHost)

        val lastTransition = runner.events.replayCache.filterIsInstance<ExchangeV2Event.Transition>().last()
        assertSame(ExchangeV2State.Host.Aborted, lastTransition.to)
    }

    @Test fun `Scanner ddg auto-elects Host when peer is 3party (both have accounts)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startScan("")

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer-user", name = "Peer", kind = "3party"),
        )

        assertSame(ExchangeV2State.Host.Confirming, runner.currentState)
    }

    @Test fun `Scanner hello during negotiating aborts and tears down the session`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startScan("")

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            runner.deliverIncomingMessage(Hello("{}"))
            val event = awaitItem()
            assertSame(ExchangeV2State.Aborted, event.to)
        }
        assertNull(runner.currentState)
    }

    @Test fun `same-account abort skips auto-election`() = runTest {
        whenever(syncStore.userId).thenReturn("shared-user")
        val runner = newRunner()
        runner.startScan("")

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            runner.deliverIncomingMessage(
                ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "shared-user", name = "Peer", kind = "ddg"),
            )
            val event = awaitItem()
            assertSame(ExchangeV2State.SameAccountAbort, event.to)
        }
        assertNull(runner.currentState)
    }

    @Test fun `Presenter auto-elects Host when both have accounts and peer is ddg (Presenter rule)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer-user", name = "Peer", kind = "ddg"),
        )

        assertSame(ExchangeV2State.Host.Confirming, runner.currentState)
    }

    @Test fun `Scanner with both accounts and peer ddg auto-elects Joiner (catch-all)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startScan("")

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer-user", name = "Peer", kind = "ddg"),
        )

        assertSame(ExchangeV2State.Joiner.Confirming, runner.currentState)
    }

    // ---- Session timeout ----

    @Test fun `session times out and tears down after the deadline`() = coroutineTestRule.testScope.runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startPresent()
        assertSame(ExchangeV2State.Bootstrapped, runner.currentState)

        advanceTimeBy(6 * 60 * 1000L) // past the 5-min session deadline

        val timedOut = runner.events.replayCache
            .filterIsInstance<ExchangeV2Event.SessionError>()
            .any { it.message.contains("timed out", ignoreCase = true) }
        assertTrue("expected a 'timed out' SessionError", timedOut)
        assertNull(runner.currentState)
    }

    @Test fun `no timeout fires after the session already reached a terminal state`() = coroutineTestRule.testScope.runTest {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "other", name = "Peer", kind = "3party"),
        )
        runner.localTrigger(LocalTrigger.UserConfirmedJoiner)
        runner.deliverIncomingMessage(RecoveryCodeResponse("{}"))
        assertNull(runner.currentState)

        advanceTimeBy(6 * 60 * 1000L)

        val timedOut = runner.events.replayCache
            .filterIsInstance<ExchangeV2Event.SessionError>()
            .any { it.message.contains("timed out", ignoreCase = true) }
        assertFalse("a completed session must not later emit a timeout", timedOut)
    }

    // ---- Poll-loop error handling ----

    @Test fun `an unexpected poll error tears down the session with a SessionError`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(channel.poll(any(), any())).thenReturn(flow<ExchangeV2Message> { throw RuntimeException("boom") })
        val runner = newRunner()
        runner.startPresent()

        val errored = runner.events.replayCache
            .filterIsInstance<ExchangeV2Event.SessionError>()
            .any { it.message.contains("boom") }
        assertTrue("expected a SessionError from the failed poll loop", errored)
        assertNull(runner.currentState)
    }

    @Test fun `polled messages are processed in wire order (hello before request reaches Host_Confirming)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(channel.poll(any(), any())).thenReturn(
            flowOf(
                Hello("""{"type":"hello"}"""),
                RecoveryCodeRequest(rawJson = "{}", name = "Joiner", kind = "ddg"),
            ),
        )

        val runner = newRunner()
        runner.startPresent()

        assertSame(ExchangeV2State.Host.Confirming, runner.currentState)
    }

    @Test fun `a polled message that reaches a terminal state tears down without a spurious error`() = runTest {
        whenever(syncStore.userId).thenReturn("shared")
        whenever(channel.poll(any(), any())).thenReturn(
            flowOf(
                ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "shared", name = "Peer", kind = "ddg"),
            ),
        )

        val runner = newRunner()
        runner.startScan("")

        assertNull(runner.currentState)
        val spurious = runner.events.replayCache
            .filterIsInstance<ExchangeV2Event.SessionError>()
            .any { it.message.contains("Pairing failed") || it.message.contains("timed out", ignoreCase = true) }
        assertFalse("terminal via poll must not surface a spurious error", spurious)
    }

    @Test fun `cancel during an open poll does not surface a spurious error`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        whenever(channel.poll(any(), any())).thenReturn(flow<ExchangeV2Message> { awaitCancellation() })
        val runner = newRunner()
        runner.startPresent()

        runner.cancel()

        val spurious = runner.events.replayCache
            .filterIsInstance<ExchangeV2Event.SessionError>()
            .any { it.message.contains("Pairing failed") }
        assertFalse("cancellation must not surface as a 'Pairing failed' error", spurious)
    }

    @Test fun `startScan with a failed channel bootstrap aborts cleanly without the misleading reach-Presenter error`() = runTest {
        whenever(channel.createChannel(any())).thenReturn(Result.Error(reason = "relay 500"))
        val runner = newRunner()
        runner.startScan("")

        val errors = runner.events.replayCache.filterIsInstance<ExchangeV2Event.SessionError>().map { it.message }
        assertTrue("expected the bootstrap error", errors.any { it.contains("Failed to create channel") })
        assertFalse("must not emit the misleading reach-Presenter error on a bootstrap failure", errors.any { it.contains("reach the Presenter") })
    }

    @Test fun `a failed bootstrap clears the pairing role so no half-started session lingers`() = runTest {
        whenever(channel.createChannel(any())).thenReturn(Result.Error(reason = "relay 500"))
        val runner = newRunner()

        runner.startScan("")

        assertNull("pairingRole must be cleared after a failed bootstrap", runner.pairingRole)
    }
}
