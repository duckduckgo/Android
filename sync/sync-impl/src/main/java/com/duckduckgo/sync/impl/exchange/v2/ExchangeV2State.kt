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
 * State machine nodes for the v2 exchange protocol.
 *
 * Spec: Asana 1215056232572322. The machine starts in [Bootstrapped], shares a
 * negotiation phase with the peer, then forks into [Host] or [Joiner] after role election.
 */
sealed interface ExchangeV2State {
    data object Bootstrapped : ExchangeV2State
    data object Negotiating : ExchangeV2State

    /**
     * Detected during Negotiating that the peer reports the same `user_id` as us — both
     * devices are already on the same account. Per spec §"Same-account case" this is **not
     * an abort**: callers should surface a friendly "Connected" finish, not an error. The
     * dispatcher maps this state to [com.duckduckgo.sync.impl.DispatchOutcome.AlreadyConnected].
     * Name retained for historical continuity; treat semantically as a success terminal.
     */
    data object SameAccountAbort : ExchangeV2State

    /**
     * Negotiation aborted before role election — e.g. an unexpected or duplicate `hello`
     * received while in [Negotiating] (a second hello, or the double-scan race). Terminal.
     * Per Unified Algorithm 1214739740392701 §Handshake Note: "abort and close the channel".
     */
    data object Aborted : ExchangeV2State

    sealed interface Host : ExchangeV2State {
        data object Confirming : Host
        data object Sending : Host
        data object Aborted : Host
        data object Done : Host
    }

    sealed interface Joiner : ExchangeV2State {
        data object Confirming : Joiner
        data object Waiting : Joiner
        data object AbortedLocal : Joiner
        data object AbortedByHost : Joiner
        data object Done : Joiner
    }
}

enum class Role {
    Host,
    Joiner,
}
