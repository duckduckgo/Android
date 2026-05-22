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
 * Spec-defined protocol actions that accompany a state transition. The state machine attaches
 * these to its [TransitionResult]; the runner executes them. Keeping side effects declared at
 * the transition (rather than scattered across the runner's post-trigger hooks) means each
 * spec rule lives in exactly one place and is easy to audit against the Unified Algorithm.
 *
 * Spec source: Asana Unified Algorithm 1214739740392701, §"Exchange Confirmations" and
 * §"Exchange Share Recovery Code".
 */
sealed interface SideEffect {

    /**
     * Send `recovery_code_awaiting_confirmation` to the peer. Per spec §"Exchange Confirmations
     * → Host" step 1, this fires when the Host enters Confirming — BEFORE the local user is
     * prompted — so the peer can render its "confirm on the other device" UX in parallel.
     */
    data object SendAwaitingConfirmation : SideEffect

    /**
     * Send `recovery_code_confirmed` to the peer. Per spec §"Exchange Confirmations → Host"
     * step 3, fires when the Host user has confirmed locally (Confirming → Sending).
     */
    data object SendConfirmed : SideEffect

    /**
     * Send `recovery_code_denied` to the peer. Per spec §"Exchange Confirmations → Host" step
     * 3 (deny branch), fires when the Host user denies (Confirming → Aborted).
     */
    data object SendDenied : SideEffect

    /**
     * Fetch this device's recovery code and share it with the peer. Per spec §"Exchange Share
     * Recovery Code → Host", fires when the Host enters Sending. The runner:
     *  1. Fetches the right recovery code for [peerKind] (ddg vs 3party), creating an account
     *     / extending with 3party credential if needed.
     *  2. Sends `recovery_code_response` (or `recovery_code_unavailable` on failure).
     *  3. Advances the SM with [LocalTrigger.HostSendComplete] or [LocalTrigger.HostUnavailable]
     *     based on the outcome.
     *
     * Composite side effect (fetch + send + advance) because the outcome is runtime-dependent;
     * the SM can't predict whether the recovery code will be available.
     */
    data object RequestRecoveryCodeShare : SideEffect
}
