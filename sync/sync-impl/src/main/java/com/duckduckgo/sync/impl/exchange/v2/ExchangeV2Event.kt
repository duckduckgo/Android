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
 * Observable events emitted by the v2 exchange runner. Downstream consumers (dev
 * screen, pixels, wide events) subscribe to the runner's event flow and react.
 */
sealed interface ExchangeV2Event {
    val timestampMs: Long

    data class Transition(
        override val timestampMs: Long,
        val from: ExchangeV2State,
        val to: ExchangeV2State,
        val trigger: ExchangeV2Message?,
        val localTrigger: LocalTrigger?,
    ) : ExchangeV2Event

    data class MessageSent(
        override val timestampMs: Long,
        val message: ExchangeV2Message,
    ) : ExchangeV2Event

    data class MessageRejected(
        override val timestampMs: Long,
        val message: ExchangeV2Message,
        val state: ExchangeV2State,
        val reason: RejectReason,
    ) : ExchangeV2Event

    /**
     * Bootstrap completed: own channel created on the relay, ephemeral keypair generated.
     * Presenter-side: [linkingCode] carries the URL to display as QR. Scanner-side: null.
     */
    data class SessionStarted(
        override val timestampMs: Long,
        val pairingRole: PairingRole,
        val ownChannelId: String,
        val linkingCode: String?,
    ) : ExchangeV2Event

    /** A transport or protocol-level failure during bootstrap / poll / send. */
    data class SessionError(
        override val timestampMs: Long,
        val message: String,
    ) : ExchangeV2Event
}

enum class RejectReason { ImplicitAbort, SameAccount, UnknownMessageDropped }
