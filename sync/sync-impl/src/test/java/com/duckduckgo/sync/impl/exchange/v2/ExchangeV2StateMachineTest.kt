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

import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Hello
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeAvailable
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeAwaitingConfirmation
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeConfirmed
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeDenied
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeRequest
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeResponse
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeUnavailable
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeV2StateMachineTest {

    private val clock = ExchangeV2Clock { 1_000L }

    private fun sm(localUserId: String? = "local-user"): ExchangeV2StateMachine =
        RealExchangeV2StateMachine(localUserId = localUserId, clock = clock)

    private fun scannerSm(localUserId: String? = "local-user"): ExchangeV2StateMachine =
        RealExchangeV2StateMachine(localUserId = localUserId, clock = clock, initialState = ExchangeV2State.Negotiating)

    private fun availableFromPeer(userId: String = "other", name: String = "Peer", kind: String = "3party") =
        RecoveryCodeAvailable(rawJson = "{}", userId = userId, name = name, kind = kind)

    private fun requestFromPeer(name: String = "Peer", kind: String = "3party") =
        RecoveryCodeRequest(rawJson = "{}", name = name, kind = kind)

    @Test fun `when hello received in Bootstrapped then transitions to Negotiating`() {
        val machine = sm()
        val result = machine.receive(Hello("{}"))

        assertSame(ExchangeV2State.Negotiating, result.newState)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
        assertSame(TransitionOutcome.Accepted, result.outcome)
        val transition = result.event as ExchangeV2Event.Transition
        assertSame(ExchangeV2State.Bootstrapped, transition.from)
        assertSame(ExchangeV2State.Negotiating, transition.to)
        assertTrue(transition.trigger is Hello)
    }

    @Test fun `when non-hello known message received in Bootstrapped then aborts`() {
        val known: List<ExchangeV2Message> = listOf(
            availableFromPeer(),
            requestFromPeer(),
            RecoveryCodeAwaitingConfirmation("{}"),
            RecoveryCodeConfirmed("{}"),
            RecoveryCodeDenied("{}"),
            RecoveryCodeUnavailable("{}"),
            RecoveryCodeResponse("{}"),
        )
        for (msg in known) {
            val machine = sm()
            val result = machine.receive(msg)
            assertTrue("expected abort for $msg, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
            assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
        }
    }

    @Test fun `when unknown message received in Bootstrapped then dropped and state unchanged`() {
        val machine = sm()
        val result = machine.receive(Unknown("{}", "future_message"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Bootstrapped, machine.currentState)
        val event = result.event as ExchangeV2Event.MessageRejected
        assertEquals(RejectReason.UnknownMessageDropped, event.reason)
    }

    @Test fun `when out-of-sequence local trigger in Bootstrapped then aborts`() {
        val machine = sm()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
    }

    @Test fun `when presenter receives second hello in Negotiating then aborts to terminal Aborted`() {
        val machine = sm()
        assertSame(ExchangeV2State.Negotiating, machine.receive(Hello("{}")).newState)

        val result = machine.receive(Hello("{}"))

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
        val transition = result.event as ExchangeV2Event.Transition
        assertSame(ExchangeV2State.Negotiating, transition.from)
        assertSame(ExchangeV2State.Aborted, transition.to)
    }

    @Test fun `when scanner receives hello in Negotiating then aborts to terminal Aborted`() {
        val machine = scannerSm()

        val result = machine.receive(Hello("{}"))

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
        val transition = result.event as ExchangeV2Event.Transition
        assertSame(ExchangeV2State.Negotiating, transition.from)
        assertSame(ExchangeV2State.Aborted, transition.to)
    }

    @Test fun `when message received in Aborted then state unchanged and aborts`() {
        val machine = scannerSm()
        machine.receive(Hello("{}"))
        assertSame(ExchangeV2State.Aborted, machine.currentState)

        val result = machine.receive(availableFromPeer())

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
    }

    @Test fun `when recovery_code_available with matching user_id received in Negotiating then transitions to SameAccountAbort`() {
        val machine = sm(localUserId = "shared-user")
        machine.receive(Hello("{}"))
        val msg = availableFromPeer(userId = "shared-user")
        val result = machine.receive(msg)

        assertEquals(ExchangeV2State.SameAccountAbort, machine.currentState)
        assertEquals(ExchangeV2State.SameAccountAbort, result.newState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertEquals(RejectReason.SameAccount, (result.outcome as TransitionOutcome.Aborted).reason)
        val event = result.event
        assertTrue("expected Transition event, got ${event::class.simpleName}", event is ExchangeV2Event.Transition)
        event as ExchangeV2Event.Transition
        assertEquals(ExchangeV2State.Negotiating, event.from)
        assertEquals(ExchangeV2State.SameAccountAbort, event.to)
        assertSame(msg, event.trigger)
        assertEquals(null, event.localTrigger)
    }

    @Test fun `when recovery_code_available with different user_id received in Negotiating then stays in Negotiating`() {
        val machine = sm(localUserId = "local-user")
        machine.receive(Hello("{}"))
        val result = machine.receive(availableFromPeer(userId = "other-user"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
        val transition = result.event as ExchangeV2Event.Transition
        assertTrue(transition.trigger is RecoveryCodeAvailable)
    }

    @Test fun `when recovery_code_request received in Negotiating then stays in Negotiating`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.receive(requestFromPeer())

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
    }

    @Test fun `when finalise-phase message received in Negotiating then aborts`() {
        val finalise: List<ExchangeV2Message> = listOf(
            RecoveryCodeAwaitingConfirmation("{}"),
            RecoveryCodeConfirmed("{}"),
            RecoveryCodeDenied("{}"),
            RecoveryCodeUnavailable("{}"),
            RecoveryCodeResponse("{}"),
        )
        for (msg in finalise) {
            val machine = sm()
            machine.receive(Hello("{}"))
            val result = machine.receive(msg)
            assertTrue("expected abort for $msg in Negotiating, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
        }
    }

    @Test fun `when RoleElected Host in Negotiating then transitions to Host Confirming`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Host))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Confirming, machine.currentState)
    }

    @Test fun `when RoleElected Joiner in Negotiating then transitions to Joiner Confirming`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Confirming, machine.currentState)
    }

    @Test fun `when RoleElected fires after peer availability then transitions to Joiner Confirming`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "other-user"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Confirming, machine.currentState)
    }

    @Test fun `when user confirms in Host Confirming then advances to Host Sending`() {
        val machine = inHostConfirming()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Sending, machine.currentState)
    }

    @Test fun `when user denies in Host Confirming then advances to Host Aborted`() {
        val machine = inHostConfirming()
        val result = machine.localTrigger(LocalTrigger.UserDeniedHost)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Aborted, machine.currentState)
    }

    @Test fun `when HostSendComplete in Host Sending then advances to Host Done`() {
        val machine = inHostConfirming()
        machine.localTrigger(LocalTrigger.UserConfirmedHost)
        val result = machine.localTrigger(LocalTrigger.HostSendComplete)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Done, machine.currentState)
    }

    @Test fun `when wire message received in Host states then aborts`() {
        val hostStateBuilders: List<Pair<String, () -> ExchangeV2StateMachine>> = listOf(
            "Host.Confirming" to ::inHostConfirming,
            "Host.Sending" to {
                inHostConfirming().also { it.localTrigger(LocalTrigger.UserConfirmedHost) }
            },
        )
        val knownIncoming: List<ExchangeV2Message> = listOf(
            Hello("{}"),
            availableFromPeer(),
            requestFromPeer(),
            RecoveryCodeResponse("{}"),
        )
        for ((label, build) in hostStateBuilders) {
            for (msg in knownIncoming) {
                val machine = build()
                val result = machine.receive(msg)
                assertTrue("$label recv $msg should abort, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
            }
        }
    }

    @Test fun `when unknown message received in Host Confirming then dropped`() {
        val machine = inHostConfirming()
        val result = machine.receive(Unknown("{}", "future"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Host.Confirming, machine.currentState)
    }

    @Test fun `when user confirms in Joiner Confirming then advances to Joiner Waiting`() {
        val machine = inJoinerConfirming()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedJoiner)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `when user denies in Joiner Confirming then advances to Joiner AbortedLocal`() {
        val machine = inJoinerConfirming()
        val result = machine.localTrigger(LocalTrigger.UserDeniedJoiner)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedLocal, machine.currentState)
    }

    @Test fun `when denied received in Joiner Confirming then transitions to Joiner AbortedByHost`() {
        val machine = inJoinerConfirming()
        val result = machine.receive(RecoveryCodeDenied("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `when unavailable received in Joiner Confirming then transitions to Joiner AbortedByHost`() {
        val machine = inJoinerConfirming()
        val result = machine.receive(RecoveryCodeUnavailable("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `when host success-phase message received in Joiner Confirming then aborts and stays put`() {
        val hostSuccessPhase: List<ExchangeV2Message> = listOf(
            RecoveryCodeAwaitingConfirmation("{}"),
            RecoveryCodeConfirmed("{}"),
            RecoveryCodeResponse("{}"),
        )
        for (msg in hostSuccessPhase) {
            val machine = inJoinerConfirming()
            val result = machine.receive(msg)
            assertTrue("recv $msg in Joiner.Confirming should abort, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
            assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
            assertSame("recv $msg should leave the SM in Joiner.Confirming", ExchangeV2State.Joiner.Confirming, machine.currentState)
        }
    }

    @Test fun `when negotiation-phase message received in Joiner Confirming then aborts`() {
        val negotiationPhase: List<ExchangeV2Message> = listOf(
            Hello("{}"),
            availableFromPeer(),
            requestFromPeer(),
        )
        for (msg in negotiationPhase) {
            val machine = inJoinerConfirming()
            val result = machine.receive(msg)
            assertTrue("recv $msg in Joiner.Confirming should abort, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
        }
    }

    @Test fun `when unknown message received in Joiner Confirming then dropped`() {
        val machine = inJoinerConfirming()
        val result = machine.receive(Unknown("{}", "future"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Joiner.Confirming, machine.currentState)
    }

    @Test fun `when awaiting_confirmation received in Joiner Waiting then stays in Joiner Waiting`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeAwaitingConfirmation("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `when confirmed received in Joiner Waiting then stays in Joiner Waiting`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeConfirmed("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `when denied received in Joiner Waiting then transitions to Joiner AbortedByHost`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeDenied("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `when unavailable received in Joiner Waiting then transitions to Joiner AbortedByHost`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeUnavailable("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `when response received in Joiner Waiting then transitions to Joiner Done`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeResponse("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Done, machine.currentState)
    }

    @Test fun `when negotiation-phase message received in Joiner Waiting then aborts`() {
        val negotiationPhase: List<ExchangeV2Message> = listOf(
            Hello("{}"),
            availableFromPeer(),
            requestFromPeer(),
        )
        for (msg in negotiationPhase) {
            val machine = inJoinerWaiting()
            val result = machine.receive(msg)
            assertTrue("recv $msg in Joiner.Waiting should abort, got ${result.outcome}", result.outcome is TransitionOutcome.Aborted)
        }
    }

    @Test fun `when unknown message received in Joiner Waiting then dropped`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(Unknown("{}", "future"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `when message received in SameAccountAbort then aborts and state unchanged`() {
        val machine = sm(localUserId = "shared-user")
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "shared-user"))
        val before = machine.currentState
        val result = machine.receive(RecoveryCodeResponse("{}"))

        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertSame(before, machine.currentState)
    }

    @Test fun `when recovery_code_available received and local user is null then stays in Negotiating`() {
        val machine = sm(localUserId = null)
        machine.receive(Hello("{}"))
        val result = machine.receive(availableFromPeer(userId = "anything"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
    }

    @Test fun `when RoleElected Host then emits SendAwaitingConfirmation side effect`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(requestFromPeer())

        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Host))

        assertEquals(listOf(SideEffect.SendAwaitingConfirmation), result.sideEffects)
    }

    @Test fun `when RoleElected Joiner then emits no side effects`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "other"))

        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertTrue("expected no side effects, got ${result.sideEffects}", result.sideEffects.isEmpty())
    }

    @Test fun `when user confirms as Host then emits SendConfirmed and RequestRecoveryCodeShare`() {
        val machine = inHostConfirming()

        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)

        assertEquals(
            listOf(SideEffect.SendConfirmed, SideEffect.RequestRecoveryCodeShare),
            result.sideEffects,
        )
    }

    @Test fun `when user denies as Host then emits SendDenied`() {
        val machine = inHostConfirming()

        val result = machine.localTrigger(LocalTrigger.UserDeniedHost)

        assertEquals(listOf(SideEffect.SendDenied), result.sideEffects)
    }

    @Test fun `when Joiner confirms or denies then emits no side effects`() {
        val confirming = inJoinerConfirming()
        val confirmed = confirming.localTrigger(LocalTrigger.UserConfirmedJoiner)
        assertTrue(confirmed.sideEffects.isEmpty())

        val denying = inJoinerConfirming()
        val denied = denying.localTrigger(LocalTrigger.UserDeniedJoiner)
        assertTrue(denied.sideEffects.isEmpty())
    }

    private fun inHostConfirming(): ExchangeV2StateMachine =
        sm().also {
            it.receive(Hello("{}"))
            it.receive(requestFromPeer())
            it.localTrigger(LocalTrigger.RoleElected(Role.Host))
        }

    private fun inJoinerConfirming(): ExchangeV2StateMachine =
        sm().also {
            it.receive(Hello("{}"))
            it.receive(availableFromPeer(userId = "other"))
            it.localTrigger(LocalTrigger.RoleElected(Role.Joiner))
        }

    private fun inJoinerWaiting(): ExchangeV2StateMachine =
        inJoinerConfirming().also {
            it.localTrigger(LocalTrigger.UserConfirmedJoiner)
        }
}
