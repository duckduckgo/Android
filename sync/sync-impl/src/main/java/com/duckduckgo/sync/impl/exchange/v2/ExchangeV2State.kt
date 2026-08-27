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
 * Every node the Exchange V2 protocol session can be in.
 *
 * The state machine exists to validate the order of messages, not their content: for a given
 * state it decides whether an inbound message is accepted, dropped, or a protocol error. States
 * therefore mirror "what are we waiting for", never "what is the UI showing".
 *
 * The session starts in a shared phase where roles aren't known yet ([Bootstrapped] →
 * [Negotiating]), then forks into the [Host] or [Joiner] substate once the runner elects a role.
 * Each fork ends in one of its own terminal states, at which point the runner tears the session
 * down: deletes its relay channel and discards the session keys. Nothing can happen after a
 * terminal, so any work that must outlive it  belongs in a non-terminal state.
 *
 * Each state below is tagged with the protocol version that introduced it. 2.1 states are only ever
 * reached when *both* peers advertised 2.1; against a 2.0 peer the session follows the 2.0 path
 * unchanged, which is what keeps the two versions interoperable.
 *
 * Spec:
 *  - Asana 1215056232572322, Exchange V2 Message Sequence State Machine: the 2.0 machine, plus the
 *    implicit-abort and unknown-message-drop rules the transitions rely on.
 *  - Asana 1216906888491126, Exchange v2.1 State machine: the delta, which is the join-status states.
 *  - Asana 1214739740392701, Unified Algorithm: role election and the abort rules the runner applies
 *    around this machine.
 */
sealed interface ExchangeV2State {

    /**
     * Presenter's start state: the linking code is published, and we're waiting for a peer to scan it
     * and send `hello`. A Scanner never sees this state: it learned the peer from the QR code, so it
     * starts in [Negotiating] (messages you send never come back to your own inbox, so a Scanner
     * could not receive its own `hello`).
     *
     * Since 2.0.
     */
    data object Bootstrapped : ExchangeV2State

    /**
     * Peer identity is established and both sides declare whether they have an account
     * (`recovery_code_available` / `recovery_code_request`). Stays here, absorbing those, until the
     * runner has enough to elect a role and fires `RoleElected`.
     *
     * Since 2.0.
     */
    data object Negotiating : ExchangeV2State

    /**
     * Terminal. Detected during [Negotiating] that the peer reports the same `user_id` as us, so
     * both devices are already on the same account. Per spec §"Same-account case" this is **not
     * an abort**: callers should surface a friendly "Connected" finish, not an error. The
     * dispatcher maps this state to [com.duckduckgo.sync.impl.DispatchOutcome.AlreadyConnected].
     * Name retained for historical continuity; treat semantically as a success terminal.
     *
     * Since 2.0.
     */
    data object SameAccountAbort : ExchangeV2State

    /**
     * Terminal. Negotiation aborted before role election, e.g. an unexpected or duplicate `hello`
     * received while in [Negotiating] (a second hello, or the double-scan race). Per Unified
     * Algorithm 1214739740392701 §Handshake Note: "abort and close the channel". The role-specific
     * forks have their own abort terminals ([Host.Aborted], [Joiner.AbortedLocal]); this one only
     * covers failures from before a role existed.
     *
     * Since 2.0.
     */
    data object Aborted : ExchangeV2State

    /**
     * The device that owns (or will create) the account and hands over the recovery code. Elected,
     * not chosen by the user: a Presenter can end up Joiner and vice versa.
     */
    sealed interface Host : ExchangeV2State {

        /**
         * Prompting our own user to approve sharing. `recovery_code_awaiting_confirmation` has
         * already gone out, so the peer can show "check the other device" while we wait.
         *
         * Since 2.0.
         */
        data object Confirming : Host

        /**
         * User approved. Provisioning whatever the peer's kind needs (create the account, add a
         * 3party credential) and sending `recovery_code_response`, or `recovery_code_unavailable`
         * if none of that can be produced.
         *
         * Since 2.0.
         */
        data object Sending : Host

        /**
         * Recovery code delivered; waiting for the Joiner to report what it did with it. Reached
         * only when the negotiated version is 2.1. A 2.0 Joiner never reports, so against one the
         * Host goes straight to [Done] exactly as it did in 2.0.
         *
         * Not terminal: the session stays alive and keeps polling until the report arrives or the
         * session deadline fires.
         *
         * Since 2.1.
         */
        data object AwaitingStatus : Host

        /**
         * Terminal. The Host side gave up: our user denied the prompt, no recovery code could be
         * produced, or the peer broke the message sequence.
         *
         * Since 2.0.
         */
        data object Aborted : Host

        /**
         * Terminal. Finished as Host. Against a 2.0 peer that means only "the recovery code was
         * sent"; against a 2.1 peer it means the Joiner reported back, and the reason it reported,
         * carried on the `recovery_code_done` that got us here, says whether it actually worked.
         *
         * Since 2.0.
         */
        data object Done : Host
    }

    /**
     * The device that receives the recovery code and joins the Host's account.
     */
    sealed interface Joiner : ExchangeV2State {

        /**
         * Prompting our own user to approve joining. Nothing is sent to the peer from here, since by
         * spec the Joiner's denial is silent, so the only inbound messages that matter are the
         * Host's own aborts.
         *
         * Since 2.0.
         */
        data object Confirming : Joiner

        /**
         * User approved; waiting on the Host. Absorbs its progress messages
         * (`recovery_code_awaiting_confirmation`, `recovery_code_confirmed`) without changing state.
         *
         * Since 2.0.
         */
        data object Waiting : Joiner

        /**
         * Recovery code received; applying it (login, 3party upgrade, initial sync) and not yet
         * done.
         *
         * Not terminal, and that is the whole point: the session has to outlive the login so
         * `recovery_code_done` can still be sent afterward. In 2.0 `recovery_code_response` landed
         * straight on [Done] because there was nothing left to report.
         *
         * Since 2.1.
         */
        data object Joining : Joiner

        /**
         * Terminal. We ended it ourselves: our user denied the prompt, or the Host broke the message
         * sequence. Nothing is sent to the peer.
         *
         * Since 2.0.
         */
        data object AbortedLocal : Joiner

        /**
         * Terminal. The Host ended it: `recovery_code_denied` (its user declined) or
         * `recovery_code_unavailable` (it couldn't produce a code).
         *
         * Since 2.0.
         */
        data object AbortedByHost : Joiner

        /**
         * Terminal. Joined successfully. Since 2.1 this is reached only after
         * `recovery_code_done(success)` has been sent, not on receiving the recovery code.
         *
         * Since 2.0.
         */
        data object Done : Joiner

        /**
         * Terminal. Failed to apply the recovery code, and reported that. Both this and [Done] are
         * reached only after `recovery_code_done` has been sent; the reason distinguishes them.
         *
         * Since 2.1.
         */
        data object JoinFailed : Joiner
    }
}

/** Which side of the exchange this device plays, decided by role election in the runner. */
enum class Role {
    Host,
    Joiner,
}
