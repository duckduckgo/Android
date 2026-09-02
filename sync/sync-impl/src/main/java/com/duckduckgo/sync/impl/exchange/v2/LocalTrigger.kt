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

import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeDone

/**
 * An input to the state machine that didn't come off the wire: a user decision, role election, or
 * the completion of work the runner was doing.
 *
 * Triggers carry no side effects themselves, but the transitions they drive declare most of them:
 * every spec-mandated message this device originates follows a local decision rather than an
 * inbound message (see [SideEffect]).
 *
 * A trigger the current state does not allow aborts the session, so the runner fires these only
 * from the state each one belongs to.
 */
sealed interface LocalTrigger {

    /**
     * Our user approved sharing the recovery code, at [ExchangeV2State.Host.Confirming]. Moves to
     * [ExchangeV2State.Host.Sending], telling the peer and starting the handover.
     */
    data object UserConfirmedHost : LocalTrigger

    /**
     * Our user refused to share, at [ExchangeV2State.Host.Confirming]. Moves to
     * [ExchangeV2State.Host.Aborted], telling the peer why the session ended.
     */
    data object UserDeniedHost : LocalTrigger

    /**
     * Our user approved joining, at [ExchangeV2State.Joiner.Confirming]. Moves to
     * [ExchangeV2State.Joiner.Waiting], which is also when the runner replays any Host messages that
     * arrived while the prompt was still up.
     */
    data object UserConfirmedJoiner : LocalTrigger

    /**
     * Our user refused to join, at [ExchangeV2State.Joiner.Confirming]. Moves to
     * [ExchangeV2State.Joiner.AbortedLocal]. Nothing is sent to the peer, since by spec the Joiner's
     * denial is silent.
     */
    data object UserDeniedJoiner : LocalTrigger

    /**
     * The runner picked which side this device plays, which is what forks the machine into the
     * [ExchangeV2State.Host] or [ExchangeV2State.Joiner] branch. Election itself lives in the runner
     * (Asana Unified Algorithm 1214739740392701), because it needs peer and account context the
     * state machine deliberately doesn't track.
     */
    data class RoleElected(val role: Role) : LocalTrigger

    /**
     * Host has finished delivering [ExchangeV2Message.RecoveryCodeResponse] (or one of its
     * negative siblings) and is leaving the [ExchangeV2State.Host.Sending] state.
     */
    data class HostSendComplete(val negotiatedVersion: ExchangeProtocolVersion.V2) : LocalTrigger

    /**
     * The Joiner has finished applying the recovery code, for better or worse. Carries the outcome
     * so the state machine can both pick a terminal and declare the `recovery_code_done` to send.
     */
    data class JoinerJoinComplete(val reason: RecoveryCodeDone.Reason) : LocalTrigger

    /**
     * Host couldn't produce a recovery code for the peer (e.g. not signed in, or no 3party
     * credential exists for a 3party peer). The runner has already sent
     * [ExchangeV2Message.RecoveryCodeUnavailable] on the wire; this drives the SM to
     * [ExchangeV2State.Host.Aborted] so the session terminates cleanly.
     */
    data object HostUnavailable : LocalTrigger
}
