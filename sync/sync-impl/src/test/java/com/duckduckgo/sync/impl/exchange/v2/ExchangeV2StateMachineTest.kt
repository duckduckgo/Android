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

    // ---------- Bootstrapped ----------

    @Test fun `bootstrapped recv hello transitions to negotiating`() {
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

    @Test fun `bootstrapped recv non-hello known message aborts`() {
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

    @Test fun `bootstrapped recv unknown drops without changing state`() {
        val machine = sm()
        val result = machine.receive(Unknown("{}", "future_message"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Bootstrapped, machine.currentState)
        val event = result.event as ExchangeV2Event.MessageRejected
        assertEquals(RejectReason.UnknownMessageDropped, event.reason)
    }

    @Test fun `bootstrapped local trigger out of sequence aborts`() {
        val machine = sm()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
    }

    // ---------- Negotiating ----------

    @Test fun `negotiating - presenter second hello aborts to terminal Aborted`() {
        val machine = sm() // starts Bootstrapped
        assertSame(ExchangeV2State.Negotiating, machine.receive(Hello("{}")).newState) // legit first hello

        val result = machine.receive(Hello("{}")) // duplicate/second hello while negotiating

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
        val transition = result.event as ExchangeV2Event.Transition
        assertSame(ExchangeV2State.Negotiating, transition.from)
        assertSame(ExchangeV2State.Aborted, transition.to)
    }

    @Test fun `negotiating - scanner hello aborts to terminal Aborted (double-scan race)`() {
        val machine = scannerSm() // Scanner starts directly in Negotiating

        val result = machine.receive(Hello("{}"))

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertEquals(RejectReason.ImplicitAbort, (result.outcome as TransitionOutcome.Aborted).reason)
        val transition = result.event as ExchangeV2Event.Transition
        assertSame(ExchangeV2State.Negotiating, transition.from)
        assertSame(ExchangeV2State.Aborted, transition.to)
    }

    @Test fun `aborted is terminal - further messages keep state and abort`() {
        val machine = scannerSm()
        machine.receive(Hello("{}"))
        assertSame(ExchangeV2State.Aborted, machine.currentState)

        val result = machine.receive(availableFromPeer())

        assertSame(ExchangeV2State.Aborted, machine.currentState)
        assertTrue(result.outcome is TransitionOutcome.Aborted)
    }

    @Test fun `negotiating recv recovery_code_available with matching user_id transitions to SameAccountAbort`() {
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

    @Test fun `negotiating recv recovery_code_available with different user_id stays in Negotiating`() {
        val machine = sm(localUserId = "local-user")
        machine.receive(Hello("{}"))
        val result = machine.receive(availableFromPeer(userId = "other-user"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
        val transition = result.event as ExchangeV2Event.Transition
        assertTrue(transition.trigger is RecoveryCodeAvailable)
    }

    @Test fun `negotiating recv recovery_code_request stays in Negotiating`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.receive(requestFromPeer())

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
    }

    @Test fun `negotiating recv finalise-phase message aborts`() {
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

    @Test fun `negotiating local trigger RoleElected Host transitions to host confirming`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Host))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Confirming, machine.currentState)
    }

    @Test fun `negotiating local trigger RoleElected Joiner transitions to joiner confirming`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Confirming, machine.currentState)
    }

    @Test fun `negotiating RoleElected can fire after receiving peer availability`() {
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "other-user"))
        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Confirming, machine.currentState)
    }

    // ---------- Host sub-machine ----------

    @Test fun `host confirming user confirmed advances to sending`() {
        val machine = inHostConfirming()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Sending, machine.currentState)
    }

    @Test fun `host confirming user denied advances to aborted`() {
        val machine = inHostConfirming()
        val result = machine.localTrigger(LocalTrigger.UserDeniedHost)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Aborted, machine.currentState)
    }

    @Test fun `host sending HostSendComplete advances to done`() {
        val machine = inHostConfirming()
        machine.localTrigger(LocalTrigger.UserConfirmedHost)
        val result = machine.localTrigger(LocalTrigger.HostSendComplete)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Host.Done, machine.currentState)
    }

    @Test fun `host states reject any incoming wire message as implicit abort`() {
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

    @Test fun `host states drop unknown messages`() {
        val machine = inHostConfirming()
        val result = machine.receive(Unknown("{}", "future"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Host.Confirming, machine.currentState)
    }

    // ---------- Joiner sub-machine ----------

    @Test fun `joiner confirming user confirmed advances to waiting`() {
        val machine = inJoinerConfirming()
        val result = machine.localTrigger(LocalTrigger.UserConfirmedJoiner)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `joiner confirming user denied advances to abortedLocal`() {
        val machine = inJoinerConfirming()
        val result = machine.localTrigger(LocalTrigger.UserDeniedJoiner)

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedLocal, machine.currentState)
    }

    @Test fun `joiner waiting recv awaiting_confirmation is idempotent self-loop`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeAwaitingConfirmation("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `joiner waiting recv confirmed is idempotent self-loop`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeConfirmed("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    @Test fun `joiner waiting recv denied transitions to abortedByHost`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeDenied("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `joiner waiting recv unavailable transitions to abortedByHost`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeUnavailable("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.AbortedByHost, machine.currentState)
    }

    @Test fun `joiner waiting recv response transitions to done`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(RecoveryCodeResponse("{}"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Joiner.Done, machine.currentState)
    }

    @Test fun `joiner waiting recv negotiation-phase message aborts`() {
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

    @Test fun `joiner waiting drops unknown messages`() {
        val machine = inJoinerWaiting()
        val result = machine.receive(Unknown("{}", "future"))

        assertSame(TransitionOutcome.Dropped, result.outcome)
        assertSame(ExchangeV2State.Joiner.Waiting, machine.currentState)
    }

    // ---------- Terminal states ----------

    @Test fun `same account abort drops further messages without state change`() {
        val machine = sm(localUserId = "shared-user")
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "shared-user"))
        val before = machine.currentState
        val result = machine.receive(RecoveryCodeResponse("{}"))

        assertTrue(result.outcome is TransitionOutcome.Aborted)
        assertSame(before, machine.currentState)
    }

    // ---------- Same-account guard with null local user ----------

    @Test fun `recovery_code_available stays in Negotiating when local user is null`() {
        val machine = sm(localUserId = null)
        machine.receive(Hello("{}"))
        val result = machine.receive(availableFromPeer(userId = "anything"))

        assertSame(TransitionOutcome.Accepted, result.outcome)
        assertSame(ExchangeV2State.Negotiating, machine.currentState)
    }

    // ---------- Side effects (spec-driven protocol messages on transitions) ----------

    @Test fun `RoleElected(Host) transition emits SendAwaitingConfirmation side effect`() {
        // Spec §"Exchange Confirmations → Host" step 1: awaiting_confirmation fires on
        // entry to Host.Confirming, BEFORE the user is prompted. The SM models this as a
        // side effect attached to the Negotiating → Host.Confirming transition.
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(requestFromPeer())

        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Host))

        assertEquals(listOf(SideEffect.SendAwaitingConfirmation), result.sideEffects)
    }

    @Test fun `RoleElected(Joiner) transition emits no side effects`() {
        // Joiner side has no spec-mandated wire message on entry — the Joiner reacts to
        // host messages, doesn't send anything itself in this phase.
        val machine = sm()
        machine.receive(Hello("{}"))
        machine.receive(availableFromPeer(userId = "other"))

        val result = machine.localTrigger(LocalTrigger.RoleElected(Role.Joiner))

        assertTrue("expected no side effects, got ${result.sideEffects}", result.sideEffects.isEmpty())
    }

    @Test fun `UserConfirmedHost transition emits SendConfirmed and RequestRecoveryCodeShare`() {
        // Spec §"Exchange Confirmations → Host" step 3 (confirm branch): send confirmed,
        // then proceed to share the recovery code with the peer.
        val machine = inHostConfirming()

        val result = machine.localTrigger(LocalTrigger.UserConfirmedHost)

        assertEquals(
            listOf(SideEffect.SendConfirmed, SideEffect.RequestRecoveryCodeShare),
            result.sideEffects,
        )
    }

    @Test fun `UserDeniedHost transition emits SendDenied`() {
        // Spec §"Exchange Confirmations → Host" step 3 (deny branch).
        val machine = inHostConfirming()

        val result = machine.localTrigger(LocalTrigger.UserDeniedHost)

        assertEquals(listOf(SideEffect.SendDenied), result.sideEffects)
    }

    @Test fun `UserConfirmedJoiner and UserDeniedJoiner transitions emit no side effects`() {
        // Joiner doesn't send anything on local confirm/deny per spec — the host-side
        // messages drive subsequent state changes.
        val confirming = inJoinerConfirming()
        val confirmed = confirming.localTrigger(LocalTrigger.UserConfirmedJoiner)
        assertTrue(confirmed.sideEffects.isEmpty())

        val denying = inJoinerConfirming()
        val denied = denying.localTrigger(LocalTrigger.UserDeniedJoiner)
        assertTrue(denied.sideEffects.isEmpty())
    }

    // ---------- Helpers ----------

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
