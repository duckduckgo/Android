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
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage

/**
 * Observable events emitted by the v2 exchange runner. Downstream consumers
 * subscribe to the runner's event flow and react.
 *
 * Purely observational: the exchange runs to completion regardless of whether anyone is listening,
 * so consumers (pixels, the pairing debug screen, UI) may drop events without affecting the protocol.
 */
sealed interface ExchangeV2Event {
    /** When the runner created the event, from its own clock, not when a consumer received it. */
    val timestampMs: Long

    /**
     * The state machine accepted a trigger and moved from [from] to [to]. Exactly one of [trigger] (an
     * inbound peer message) and [localTrigger] (a user or runner decision) is set; an abort driven by a
     * peer message arrives here rather than as [MessageRejected] whenever [from] was still active.
     */
    data class Transition(
        override val timestampMs: Long,
        val from: ExchangeV2State,
        val to: ExchangeV2State,
        val trigger: ExchangeV2Message?,
        val localTrigger: LocalTrigger?,
    ) : ExchangeV2Event

    /**
     * [message] was accepted by the relay for delivery to the peer. Says nothing about the peer having
     * polled it: the relay accepts messages for a channel the peer has already abandoned.
     */
    data class MessageSent(
        override val timestampMs: Long,
        val message: ExchangeV2Message,
    ) : ExchangeV2Event

    /**
     * [message] was received but not acted on in [state]: either an unknown message type dropped to keep
     * the session alive, or a protocol violation aborting a session already in a terminal state (an abort
     * from an active state surfaces as a [Transition] instead). See [RejectReason].
     */
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

    /**
     * A transport or protocol-level failure while bootstrapping, polling, or sending. Terminal: the runner tears the
     * session down after emitting, and emits at most one per session. [message] is developer-facing text;
     * [kind] is the value to branch on. [timeoutStage] is set only for [SessionErrorKind.SessionTimeout].
     */
    data class SessionError(
        override val timestampMs: Long,
        val message: String,
        val kind: SessionErrorKind = SessionErrorKind.Unknown,
        val timeoutStage: TimeoutStage? = null,
    ) : ExchangeV2Event

    /**
     * The peer's advertised protocol version became known and both sides settled on [negotiatedVersion],
     * the lower of [ourVersion] and [peerVersion], falling back to the baseline when the peer advertises
     * something we can't parse. Emitted once per side, at the point named by [peerSource]: the Scanner
     * learns the version from the linking code, the Presenter from the peer's hello.
     */
    data class VersionNegotiated(
        override val timestampMs: Long,
        val peerSource: PeerVersionSource,
        val peerVersion: ExchangeProtocolVersion,
        val ourVersion: ExchangeProtocolVersion,
        val negotiatedVersion: ExchangeProtocolVersion,
    ) : ExchangeV2Event
}

/** Why a received message was not acted on. See [ExchangeV2Event.MessageRejected]. */
enum class RejectReason {
    /** A known message type that the current state does not allow: a protocol violation, aborts the session. */
    ImplicitAbort,

    /** The peer turned out to be the same sync account as us, so there is nothing to pair. */
    SameAccount,

    /** An unrecognized message type, ignored so a newer peer's extra messages can't kill the session. */
    UnknownMessageDropped,
}

/**
 * What went wrong in a [ExchangeV2Event.SessionError], as a bounded value consumers can branch on.
 * Mapped to sync pixel error codes, so treat the entries as part of the telemetry contract.
 */
enum class SessionErrorKind {
    /** No progress within the deadline for the current stage; the stage is carried in `timeoutStage`. */
    SessionTimeout,

    /** A second hello arrived after negotiation had already started. */
    UnexpectedSecondHello,

    /** A protocol violation that isn't covered by a more specific kind. Not currently emitted by the runner. */
    UnexpectedEvent,

    /** An outbound message was attempted before the local session had the keys and channel ids it needs. */
    PairingSessionNotReady,

    /** The relay could not create the channel, or reported ours or the peer's channel as gone. */
    RelayChannelUnavailable,

    /** We failed to produce the recovery code to hand over, e.g. account creation failed. */
    RecoveryCodePreparationFailed,

    /** The relay rejected our request as malformed. */
    MalformedRelayRequest,

    /** Anything else, including generic transport failures. */
    Unknown,
}

/** Where the peer's advertised version was read from. See [ExchangeV2Event.VersionNegotiated]. */
enum class PeerVersionSource {
    /** Scanner side: parsed out of the linking code before any message is exchanged. */
    LinkingCode,

    /** Presenter side: taken from the peer's hello. */
    HelloMessage,
}
