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
 * Side-effect-free transitions driven by something other than an inbound wire message
 * (user input, role election by the runner, completion of an outbound send).
 */
sealed interface LocalTrigger {
    data object UserConfirmedHost : LocalTrigger
    data object UserDeniedHost : LocalTrigger
    data object UserConfirmedJoiner : LocalTrigger
    data object UserDeniedJoiner : LocalTrigger
    data class RoleElected(val role: Role) : LocalTrigger

    /**
     * Host has finished delivering [ExchangeV2Message.RecoveryCodeResponse] (or one of its
     * negative siblings) and is leaving the [ExchangeV2State.Host.Sending] state.
     */
    data object HostSendComplete : LocalTrigger

    /**
     * Host couldn't produce a recovery code for the peer (e.g. not signed in, or no 3party
     * credential exists for a 3party peer). The runner has already sent
     * [ExchangeV2Message.RecoveryCodeUnavailable] on the wire; this drives the SM to
     * [ExchangeV2State.Host.Aborted] so the session terminates cleanly.
     */
    data object HostUnavailable : LocalTrigger
}
