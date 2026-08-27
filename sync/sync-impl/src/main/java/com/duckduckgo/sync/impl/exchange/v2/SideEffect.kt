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

/**
 * A protocol action the runner must perform because a transition happened.
 *
 * The state machine is a pure validator: it decides the next state and declares what the spec
 * requires alongside it, but never touches the network itself. The runner executes these in list
 * order, after the transition has already been applied and its event emitted. Two consequences
 * follow from that:
 *  - Order within one [TransitionResult] is significant. [SendConfirmed] ahead of
 *    [RequestRecoveryCodeShare] is what puts `recovery_code_confirmed` on the wire before the
 *    recovery code itself.
 *  - No state may depend on an effect succeeding. The state has already moved by the time one runs,
 *    so a failed send surfaces as a session error and is never rolled back or retried.
 *
 * Declaring effects at the transition, rather than scattering them across the runner's post-trigger
 * hooks, keeps each spec rule in exactly one place and auditable against the spec.
 *
 * Spec: Asana 1214739740392701, Unified Algorithm: §"Exchange Confirmations" and §"Exchange Share
 * Recovery Code" define the effects and the point at which each fires.
 */
sealed interface SideEffect {

    /**
     * Send `recovery_code_awaiting_confirmation`.
     *
     * Fires on entry to [ExchangeV2State.Host.Confirming], deliberately before our own user prompt,
     * so the Joiner can show "check the other device" while the Host's user is still deciding.
     *
     * Since 2.0.
     */
    data object SendAwaitingConfirmation : SideEffect

    /**
     * Send `recovery_code_confirmed`: our user approved sharing. Paired with
     * [RequestRecoveryCodeShare] on the same transition and ordered first, so the peer learns the
     * decision before the code arrives.
     *
     * Since 2.0.
     */
    data object SendConfirmed : SideEffect

    /**
     * Send `recovery_code_denied`: our user refused, on the deny branch out of
     * [ExchangeV2State.Host.Confirming]. The Joiner has no equivalent, because by spec its denial is
     * silent.
     *
     * Since 2.0.
     */
    data object SendDenied : SideEffect

    /**
     * Produce and deliver the recovery code. The one effect that is not a single message send, and
     * the one that feeds back into the machine.
     *
     * The runner provisions whatever the peer's kind needs (create the account, add a 3party
     * credential), then sends `recovery_code_response`, or `recovery_code_unavailable` if none of
     * that could be produced. Because that involves network calls it runs asynchronously, and
     * finishes by firing [LocalTrigger.HostSendComplete] or [LocalTrigger.HostUnavailable]. Those
     * triggers, not this effect, are what move the Host off [ExchangeV2State.Host.Sending].
     *
     * Since 2.0.
     */
    data object RequestRecoveryCodeShare : SideEffect
}
