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
import com.duckduckgo.sync.impl.crypto.RsaKeyPair
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Hello
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeRequest
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.runTest
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
            appScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )

    @Before fun stubWireDeps() {
        // Tests exercise SM-driving + auto-elect behaviour, not wire I/O. Stub everything
        // that touches the network or generates real keys.
        whenever(qrCode.parse(any())).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "peer-channel", publicKey = "peer-pubkey", version = "2"),
        )
        whenever(qrCode.buildLinkingCode(any(), any(), any())).thenReturn("https://duckduckgo.com/sync/pairing/#&code2=fake")
        whenever(jweCrypto.generateRsaKeyPair()).thenReturn(RsaKeyPair(publicKeyBase64 = "own-pub", privateKeyBase64 = "own-priv"))
        whenever(channel.createChannel(any())).thenReturn(Result.Success(Unit))
        whenever(channel.poll(any(), any())).thenReturn(emptyFlow())
        whenever(channel.sendMessage(any(), any(), any(), any())).thenReturn(Result.Success(Unit))
        whenever(channel.deleteChannel(any())).thenReturn(Result.Success(Unit))
    }

    @Test fun `startScan creates a session in Negotiating (Scanner already knows the peer)`() {
        val runner = newRunner()
        runner.startScan(pastedUrl = "ignored")

        assertSame(ExchangeV2State.Negotiating, runner.currentState)
    }

    @Test fun `cancel abandons the session`() {
        val runner = newRunner()
        runner.startScan("")
        runner.cancel()

        assertNull(runner.currentState)
    }

    @Test fun `startScan called twice replaces the existing session`() {
        val runner = newRunner()
        runner.startScan("")
        // Drive the first session along so it could leak if not cleared.
        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer", name = "Peer", kind = "3party"),
        )
        // Session should now have absorbed peer context.

        runner.startScan("")

        // After re-start, role is set fresh and SM is back in Negotiating with no peer context applied yet.
        assertSame(ExchangeV2State.Negotiating, runner.currentState)
        assertSame(PairingRole.Scanner, runner.pairingRole)
    }

    @Test fun `deliverIncomingMessage forwards SM event to flow`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user") // canStartAsPresenter requires a signed-in account
        val runner = newRunner()
        runner.startPresent() // Presenter starts in Bootstrapped — receives hello to enter Negotiating.

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
        runner.deliverIncomingMessage(Hello("{}")) // → Negotiating

        runner.events.filterIsInstance<ExchangeV2Event.Transition>().test {
            // Drain replay: SessionStarted is filtered out; Negotiating transition replays.
            awaitItem()
            runner.localTrigger(LocalTrigger.RoleElected(Role.Host))
            val event = awaitItem()
            assertSame(ExchangeV2State.Host.Confirming, event.to)
        }
    }

    @Test fun `terminal state abandons session`() {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(Hello("{}"))
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

        runner.events.filterIsInstance<ExchangeV2Event.MessageRejected>().test {
            runner.deliverIncomingMessage(
                ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "shared-user", name = "Peer", kind = "3party"),
            )
            val event = awaitItem()
            assertSame(RejectReason.SameAccount, event.reason)
        }
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

        // Per spec §"Exchange Share Recovery Code": Host may create its account during pairing.
        // Therefore the runner must not pre-flight-block a not-signed-in device from being Presenter.
        assertSame(PairingRole.Presenter, runner.pairingRole)
        assertSame(ExchangeV2State.Bootstrapped, runner.currentState)
    }

    @Test fun `canStartAsPresenter is true regardless of account state`() {
        whenever(syncStore.userId).thenReturn(null)
        assertTrue(newRunner().canStartAsPresenter)
        whenever(syncStore.userId).thenReturn("my-user")
        assertTrue(newRunner().canStartAsPresenter)
    }

    @Test fun `cancel clears pairingRole`() {
        val runner = newRunner()
        runner.startScan("")
        runner.cancel()

        assertNull(runner.pairingRole)
    }

    @Test fun `Scanner with no account auto-elects Joiner when peer (ddg) has account`() = runTest {
        whenever(syncStore.userId).thenReturn(null)
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "host-user", name = "Host", kind = "ddg"),
        )

        // Auto-elect drives us straight to Joiner.Confirming without a manual RoleElected call.
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

        // Per spec §"Exchange Confirmations → Host": awaiting_confirmation must be sent
        // BEFORE the user is prompted (i.e., on entry to Confirming), so the peer can render
        // its "confirm on the other device" UX. The user hasn't tapped UserConfirmedHost yet.
        verify(channel).sendMessage(
            argThat { contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
        // recovery_code_confirmed must NOT have been sent yet (no user confirm trigger).
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
        // Reach Host.Confirming — awaiting_confirmation has been sent once.
        // Now user confirms locally.
        runner.localTrigger(LocalTrigger.UserConfirmedHost)

        // recovery_code_confirmed was sent after UserConfirmedHost.
        verify(channel).sendMessage(
            argThat { contains("recovery_code_confirmed") && !contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
        // awaiting_confirmation was sent only ONCE in the whole flow (on entry to Confirming),
        // not a second time on UserConfirmedHost. Mockito's `times(1)` would be ideal but
        // matchers across JSON-string args make argThat-with-count flaky — use never() on a
        // payload that is *exactly* the awaiting_confirmation JSON-with-nothing-else, ensuring
        // we never sent it twice. (One send is matched by the prior verify; a second would
        // mean we double-sent.)
        verify(channel, org.mockito.kotlin.times(1)).sendMessage(
            argThat { contains("recovery_code_awaiting_confirmation") },
            any(),
            any(),
            any(),
        )
    }

    @Test fun `Scanner ddg auto-elects Host when peer is 3party (both have accounts)`() = runTest {
        whenever(syncStore.userId).thenReturn("my-user")
        val runner = newRunner()
        runner.startScan("")
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer-user", name = "Peer", kind = "3party"),
        )

        // ddg-vs-3party rule wins over Presenter/Scanner — even though we scanned, ddg becomes Host.
        assertSame(ExchangeV2State.Host.Confirming, runner.currentState)
    }

    @Test fun `same-account abort skips auto-election`() = runTest {
        whenever(syncStore.userId).thenReturn("shared-user")
        val runner = newRunner()
        runner.startScan("")

        runner.events.filterIsInstance<ExchangeV2Event.MessageRejected>().test {
            runner.deliverIncomingMessage(
                ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "shared-user", name = "Peer", kind = "ddg"),
            )
            val event = awaitItem()
            assertSame(RejectReason.SameAccount, event.reason)
        }
        // No follow-up Transition for RoleElected — auto-elect must skip after a rejected outcome.
        // SameAccountAbort is terminal → runner clears the session.
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
        runner.deliverIncomingMessage(Hello("{}"))

        runner.deliverIncomingMessage(
            ExchangeV2Message.RecoveryCodeAvailable(rawJson = "{}", userId = "peer-user", name = "Peer", kind = "ddg"),
        )

        assertSame(ExchangeV2State.Joiner.Confirming, runner.currentState)
    }
}
