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
import javax.inject.Inject

/**
 * Pure validator for the Exchange V2 wire protocol. Stateful (it tracks [currentState] and the
 * local account's user_id for same-account detection) but performs no side effects itself: every
 * input returns a [TransitionResult] that the runner forwards to the event sink and whose declared
 * [SideEffect]s the runner executes.
 *
 * Spec: Asana 1215056232572322 — Exchange V2 Message Sequence State Machine.
 */
interface ExchangeV2StateMachine {
    val currentState: ExchangeV2State
    fun receive(msg: ExchangeV2Message): TransitionResult
    fun localTrigger(trigger: LocalTrigger): TransitionResult
}

class ExchangeV2StateMachineFactory @Inject constructor(
    private val clock: ExchangeV2Clock,
) {
    /**
     * Create a fresh state machine.
     *
     * @param localUserId own account user_id (or null when no account), used for same-account detection.
     * @param initialState where to start. Presenter starts in [ExchangeV2State.Bootstrapped]
     *  and transitions to [ExchangeV2State.Negotiating] on receiving a hello. Scanner has
     *  already parsed the peer's code and is ready to negotiate; it starts in
     *  [ExchangeV2State.Negotiating] directly. (See Transport TD Notes[2]: messages you send
     *  do not return to your own inbox, so a Scanner can never "receive" its own hello.)
     */
    fun create(
        localUserId: String?,
        initialState: ExchangeV2State = ExchangeV2State.Bootstrapped,
    ): ExchangeV2StateMachine =
        RealExchangeV2StateMachine(localUserId = localUserId, clock = clock, initialState = initialState)
}

/** Indirection so tests can supply a fixed timestamp. */
fun interface ExchangeV2Clock {
    fun nowMs(): Long
}

internal class RealExchangeV2StateMachine(
    private val localUserId: String?,
    private val clock: ExchangeV2Clock,
    initialState: ExchangeV2State = ExchangeV2State.Bootstrapped,
) : ExchangeV2StateMachine {

    override var currentState: ExchangeV2State = initialState
        private set

    override fun receive(msg: ExchangeV2Message): TransitionResult {
        if (msg is Unknown) return drop(msg)
        return when (val state = currentState) {
            ExchangeV2State.Bootstrapped -> receiveInBootstrapped(state, msg)
            ExchangeV2State.Negotiating -> receiveInNegotiating(state, msg)
            ExchangeV2State.Joiner.Confirming -> receiveInJoinerConfirming(state, msg)
            ExchangeV2State.Joiner.Waiting -> receiveInJoinerWaiting(state, msg)
            else -> abort(state, msg, RejectReason.ImplicitAbort)
        }
    }

    override fun localTrigger(trigger: LocalTrigger): TransitionResult {
        return when (val state = currentState) {
            ExchangeV2State.Negotiating -> localTriggerInNegotiating(state, trigger)
            ExchangeV2State.Host.Confirming -> localTriggerInHostConfirming(state, trigger)
            ExchangeV2State.Host.Sending -> localTriggerInHostSending(state, trigger)
            ExchangeV2State.Joiner.Confirming -> localTriggerInJoinerConfirming(state, trigger)
            else -> abortLocal(state, trigger)
        }
    }

    private fun receiveInBootstrapped(state: ExchangeV2State, msg: ExchangeV2Message): TransitionResult {
        return if (msg is Hello) {
            accept(state, ExchangeV2State.Negotiating, msg)
        } else {
            abort(state, msg, RejectReason.ImplicitAbort)
        }
    }

    private fun receiveInNegotiating(state: ExchangeV2State, msg: ExchangeV2Message): TransitionResult {
        return when (msg) {
            // By the time we are in Negotiating the peer hello is already established
            // (Scanner: by scanning the QR; Presenter: consumed in receiveInBootstrapped).
            // Any further hello is a duplicate or the double-scan race → abort and close the
            // channel. Deliberate "record a pixel; abort (scope-cut)" (pixel deferred: Asana 1215473364991760).
            is Hello -> abort(state, msg, RejectReason.ImplicitAbort)
            is RecoveryCodeAvailable -> {
                if (localUserId != null && msg.userId == localUserId) {
                    abort(state, msg, RejectReason.SameAccount, newState = ExchangeV2State.SameAccountAbort)
                } else {
                    // Role election lives in the runner: this device records the peer's
                    // availability and stays in Negotiating until LocalTrigger.RoleElected fires.
                    accept(state, ExchangeV2State.Negotiating, msg)
                }
            }
            // Both availability messages keep us in Negotiating; the runner combines them
            // with own account state, own/peer kind, scan order, and channel_id tiebreak
            // before electing a role (see Asana Unified Algorithm 1214739740392701).
            is RecoveryCodeRequest -> accept(state, ExchangeV2State.Negotiating, msg)
            is RecoveryCodeAwaitingConfirmation,
            is RecoveryCodeConfirmed,
            is RecoveryCodeDenied,
            is RecoveryCodeUnavailable,
            is RecoveryCodeResponse,
            -> abort(state, msg, RejectReason.ImplicitAbort)
            is Unknown -> drop(msg)
        }
    }

    // If the peer aborts while we're still showing the confirm prompt, act on it now instead of
    // making the user confirm a doomed pairing.
    private fun receiveInJoinerConfirming(state: ExchangeV2State, msg: ExchangeV2Message): TransitionResult {
        return when (msg) {
            is RecoveryCodeDenied -> accept(state, ExchangeV2State.Joiner.AbortedByHost, msg)
            is RecoveryCodeUnavailable -> accept(state, ExchangeV2State.Joiner.AbortedByHost, msg)
            else -> abort(state, msg, RejectReason.ImplicitAbort)
        }
    }

    private fun receiveInJoinerWaiting(state: ExchangeV2State, msg: ExchangeV2Message): TransitionResult {
        return when (msg) {
            is RecoveryCodeAwaitingConfirmation -> accept(state, ExchangeV2State.Joiner.Waiting, msg)
            is RecoveryCodeConfirmed -> accept(state, ExchangeV2State.Joiner.Waiting, msg)
            is RecoveryCodeDenied -> accept(state, ExchangeV2State.Joiner.AbortedByHost, msg)
            is RecoveryCodeUnavailable -> accept(state, ExchangeV2State.Joiner.AbortedByHost, msg)
            is RecoveryCodeResponse -> accept(state, ExchangeV2State.Joiner.Done, msg)
            is Hello,
            is RecoveryCodeAvailable,
            is RecoveryCodeRequest,
            -> abort(state, msg, RejectReason.ImplicitAbort)
            is Unknown -> drop(msg)
        }
    }

    private fun localTriggerInNegotiating(state: ExchangeV2State, trigger: LocalTrigger): TransitionResult {
        return when (trigger) {
            is LocalTrigger.RoleElected -> when (trigger.role) {
                // Spec "Exchange Confirmations → Host" step 1: send awaiting_confirmation
                // on entry to Confirming, BEFORE the user prompt fires, so the peer can show
                // its "confirm on the other device" UX in parallel.
                Role.Host -> acceptLocal(
                    state,
                    ExchangeV2State.Host.Confirming,
                    trigger,
                    sideEffects = listOf(SideEffect.SendAwaitingConfirmation),
                )
                Role.Joiner -> acceptLocal(state, ExchangeV2State.Joiner.Confirming, trigger)
            }
            else -> abortLocal(state, trigger)
        }
    }

    private fun localTriggerInHostConfirming(state: ExchangeV2State, trigger: LocalTrigger): TransitionResult {
        return when (trigger) {
            // Spec "Exchange Confirmations → Host" step 3 (confirm branch): send confirmed,
            // then proceed to share the recovery code.
            LocalTrigger.UserConfirmedHost -> acceptLocal(
                state,
                ExchangeV2State.Host.Sending,
                trigger,
                sideEffects = listOf(SideEffect.SendConfirmed, SideEffect.RequestRecoveryCodeShare),
            )
            // Spec "Exchange Confirmations → Host" step 3 (deny branch): send denied, abort.
            LocalTrigger.UserDeniedHost -> acceptLocal(
                state,
                ExchangeV2State.Host.Aborted,
                trigger,
                sideEffects = listOf(SideEffect.SendDenied),
            )
            else -> abortLocal(state, trigger)
        }
    }

    private fun localTriggerInHostSending(state: ExchangeV2State, trigger: LocalTrigger): TransitionResult {
        return when (trigger) {
            LocalTrigger.HostSendComplete -> acceptLocal(state, ExchangeV2State.Host.Done, trigger)
            // Host couldn't produce a recovery code (no account, no 3party credential, etc.).
            // Runner has already sent recovery_code_unavailable to peer; this just tears down.
            LocalTrigger.HostUnavailable -> acceptLocal(state, ExchangeV2State.Host.Aborted, trigger)
            else -> abortLocal(state, trigger)
        }
    }

    private fun localTriggerInJoinerConfirming(state: ExchangeV2State, trigger: LocalTrigger): TransitionResult {
        return when (trigger) {
            LocalTrigger.UserConfirmedJoiner -> acceptLocal(state, ExchangeV2State.Joiner.Waiting, trigger)
            LocalTrigger.UserDeniedJoiner -> acceptLocal(state, ExchangeV2State.Joiner.AbortedLocal, trigger)
            else -> abortLocal(state, trigger)
        }
    }

    private fun accept(
        from: ExchangeV2State,
        to: ExchangeV2State,
        msg: ExchangeV2Message,
    ): TransitionResult {
        currentState = to
        return TransitionResult(
            newState = to,
            event = ExchangeV2Event.Transition(clock.nowMs(), from, to, trigger = msg, localTrigger = null),
            outcome = TransitionOutcome.Accepted,
        )
    }

    private fun acceptLocal(
        from: ExchangeV2State,
        to: ExchangeV2State,
        trigger: LocalTrigger,
        sideEffects: List<SideEffect> = emptyList(),
    ): TransitionResult {
        currentState = to
        return TransitionResult(
            newState = to,
            event = ExchangeV2Event.Transition(clock.nowMs(), from, to, trigger = null, localTrigger = trigger),
            outcome = TransitionOutcome.Accepted,
            sideEffects = sideEffects,
        )
    }

    /**
     * The SM rejected [msg] in state [from]. When [newState] == [from] the SM stays put (e.g.
     * a duplicate `hello` in Negotiating) and the event is a [MessageRejected]; when [newState]
     * differs the SM is actually transitioning into [newState] driven by [msg] (e.g. same-account
     * detected, per Asana state-machine spec `1215056232572322`), and we emit a [Transition] so
     * downstream consumers can react to the new terminal state.
     */
    private fun abort(
        from: ExchangeV2State,
        msg: ExchangeV2Message,
        reason: RejectReason,
        newState: ExchangeV2State = from.abortTerminal(),
    ): TransitionResult {
        currentState = newState
        val event = if (newState == from) {
            ExchangeV2Event.MessageRejected(clock.nowMs(), msg, from, reason)
        } else {
            ExchangeV2Event.Transition(
                timestampMs = clock.nowMs(),
                from = from,
                to = newState,
                trigger = msg,
                localTrigger = null,
            )
        }
        return TransitionResult(
            newState = newState,
            event = event,
            outcome = TransitionOutcome.Aborted(reason),
        )
    }

    private fun abortLocal(
        from: ExchangeV2State,
        trigger: LocalTrigger,
    ): TransitionResult {
        val newState = from.abortTerminal()
        currentState = newState
        return TransitionResult(
            newState = newState,
            event = ExchangeV2Event.Transition(clock.nowMs(), from, newState, trigger = null, localTrigger = trigger),
            outcome = TransitionOutcome.Aborted(RejectReason.ImplicitAbort),
        )
    }

    private fun drop(msg: ExchangeV2Message): TransitionResult {
        return TransitionResult(
            newState = currentState,
            event = ExchangeV2Event.MessageRejected(clock.nowMs(), msg, currentState, RejectReason.UnknownMessageDropped),
            outcome = TransitionOutcome.Dropped,
        )
    }
}

/** Terminal state to drive into on an implicit abort from [this]. Terminals return themselves. */
private fun ExchangeV2State.abortTerminal(): ExchangeV2State = when (this) {
    ExchangeV2State.Host.Confirming, ExchangeV2State.Host.Sending -> ExchangeV2State.Host.Aborted
    ExchangeV2State.Joiner.Confirming, ExchangeV2State.Joiner.Waiting -> ExchangeV2State.Joiner.AbortedLocal
    ExchangeV2State.Bootstrapped, ExchangeV2State.Negotiating -> ExchangeV2State.Aborted
    ExchangeV2State.Aborted,
    ExchangeV2State.SameAccountAbort,
    ExchangeV2State.Host.Aborted,
    ExchangeV2State.Host.Done,
    ExchangeV2State.Joiner.AbortedByHost,
    ExchangeV2State.Joiner.AbortedLocal,
    ExchangeV2State.Joiner.Done,
    -> this
}
