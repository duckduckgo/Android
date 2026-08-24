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
import org.json.JSONObject

/**
 * Wire messages exchanged over the v2 /sync/v2/exchange/{channelId} channel.
 *
 * Each message is a JSON object identified by its `type` field, carried inside an encrypted relay
 * envelope. The envelope is not a message type itself: it holds `version` (the protocol version
 * required to process it) and `payload` (the JWE compact serialization of the encrypted message
 * JSON, whose `kid` header is the sender's channel ID).
 *
 * Spec: Asana 1215056232572321 (Exchange V2 Messages), 1215056232572322 (Exchange V2 Message
 * Sequence State Machine).
 */
sealed interface ExchangeV2Message {
    /**
     * The message JSON exactly as it goes on (or came off) the wire, before envelope encryption.
     * Kept verbatim so a received message can be re-read or logged without lossy reserialization,
     * including any fields this version of the client doesn't model.
     */
    val rawJson: String

    /** Value of the message's `type` field, which identifies the message on the wire. */
    val messageType: String

    /**
     * Lowest protocol version a receiver needs in order to process this message — what goes into
     * `envelope.version`. A property of the message, not of the sending device, and unrelated to
     * the version negotiated for the session.
     */
    val protocolVersion: ExchangeProtocolVersion.V2

    /**
     * Sent by the Scanner to the Presenter's relay channel to open the session.
     *
     * @param channelId Scanner's own relay channel ID — the Presenter learns this here and uses
     *  it as the address for replies.
     * @param publicKey Scanner's session public key (base64url-encoded SPKI DER); used to
     *  encrypt all subsequent messages addressed to the Scanner.
     * @param version The version the sender speaks (e.g. "2", "2.1"; defaults to "2"). Not a
     *  minimum requirement on the receiver — that is `envelope.version`. It lets the peer adjust
     *  its capabilities, and is the input to capability negotiation.
     */
    @ConsistentCopyVisibility
    data class Hello private constructor(
        override val rawJson: String,
        val channelId: String,
        val publicKey: String,
        val version: ExchangeProtocolVersion,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "hello"

            private val DEFAULT_VERSION = ExchangeProtocolVersion.V2_0

            private const val FIELD_CHANNEL_ID = "channel_id"
            private const val FIELD_PUBLIC_KEY = "public_key"
            private const val FIELD_VERSION = "version"

            fun create(
                channelId: String,
                publicKey: String,
                version: ExchangeProtocolVersion,
            ) = Hello(
                rawJson = buildMessageJson(TYPE) {
                    put(FIELD_CHANNEL_ID, channelId)
                    put(FIELD_PUBLIC_KEY, publicKey)
                    put(FIELD_VERSION, version.toString())
                },
                channelId = channelId,
                publicKey = publicKey,
                version = version,
            )

            fun fromJson(rawJson: String): Hello {
                val json = JSONObject(rawJson)
                val rawVersion = json.optString(FIELD_VERSION, "")
                return Hello(
                    rawJson = rawJson,
                    channelId = json.optString(FIELD_CHANNEL_ID, ""),
                    publicKey = json.optString(FIELD_PUBLIC_KEY, ""),
                    // Spec: an absent version means the sender speaks 2.
                    version = if (rawVersion.isBlank()) DEFAULT_VERSION else ExchangeProtocolVersion.parseOrUnsupported(rawVersion),
                )
            }
        }
    }

    /**
     * Sent by either device during negotiation to declare it has a sync account.
     *
     * @param userId Identifies the sender's account. If both sides send the same [userId] the
     *  session must be aborted — the devices are already on the same account.
     * @param name Human-readable device name shown to the peer.
     * @param kind Device kind, "ddg" or "3party"; drives cross-kind role election.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeAvailable private constructor(
        override val rawJson: String,
        val userId: String,
        val name: String,
        val kind: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_available"

            private const val FIELD_USER_ID = "user_id"
            private const val FIELD_NAME = "name"
            private const val FIELD_KIND = "kind"

            fun create(
                userId: String,
                name: String,
                kind: String,
            ) = RecoveryCodeAvailable(
                rawJson = buildMessageJson(TYPE) {
                    put(FIELD_USER_ID, userId)
                    put(FIELD_NAME, name)
                    put(FIELD_KIND, kind)
                },
                userId = userId,
                name = name,
                kind = kind,
            )

            // NOTE: a recovery_code_available semantically must carry a user_id (the sender has
            // an account), but we currently accept a missing/blank one as userId="" rather than
            // rejecting it — downstream this affects same-account detection and role election.
            // Hardening (reject or drop as malformed) is tracked separately in Asana
            // 1215414930715623; left lenient here for forward-compat.
            fun fromJson(rawJson: String): RecoveryCodeAvailable {
                val json = JSONObject(rawJson)
                return RecoveryCodeAvailable(
                    rawJson = rawJson,
                    userId = json.optString(FIELD_USER_ID, ""),
                    name = json.optString(FIELD_NAME, ""),
                    kind = json.optString(FIELD_KIND, ""),
                )
            }
        }
    }

    /**
     * Sent by either device during negotiation to declare it has no sync account. No user_id
     * accompanies it.
     *
     * @param name Human-readable device name shown to the peer.
     * @param kind Device kind, "ddg" or "3party"; drives cross-kind role election.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeRequest private constructor(
        override val rawJson: String,
        val name: String,
        val kind: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_request"

            private const val FIELD_NAME = "name"
            private const val FIELD_KIND = "kind"

            fun create(
                name: String,
                kind: String,
            ) = RecoveryCodeRequest(
                rawJson = buildMessageJson(TYPE) {
                    put(FIELD_NAME, name)
                    put(FIELD_KIND, kind)
                },
                name = name,
                kind = kind,
            )

            fun fromJson(rawJson: String): RecoveryCodeRequest {
                val json = JSONObject(rawJson)
                return RecoveryCodeRequest(
                    rawJson = rawJson,
                    name = json.optString(FIELD_NAME, ""),
                    kind = json.optString(FIELD_KIND, ""),
                )
            }
        }
    }

    /**
     * Sent by the Host to signal it is currently prompting its user.
     *
     * The Joiner already confirmed locally before entering the message loop, so no action is
     * required. A UI may use it to show a hint such as "Check the other device".
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeAwaitingConfirmation private constructor(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_awaiting_confirmation"

            fun create() = RecoveryCodeAwaitingConfirmation(buildMessageJson(TYPE))

            fun fromJson(rawJson: String) = RecoveryCodeAwaitingConfirmation(rawJson)
        }
    }

    /**
     * Sent by the Host after its user approved, signaling the recovery code is about to follow.
     *
     * The Joiner must accept [RecoveryCodeResponse] regardless of whether this message was seen
     * first.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeConfirmed private constructor(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_confirmed"

            fun create() = RecoveryCodeConfirmed(buildMessageJson(TYPE))

            fun fromJson(rawJson: String) = RecoveryCodeConfirmed(rawJson)
        }
    }

    /**
     * Sent by the Host when its user declined to share the recovery code. The Joiner must abort the
     * session on receiving this.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeDenied private constructor(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_denied"

            fun create() = RecoveryCodeDenied(buildMessageJson(TYPE))

            fun fromJson(rawJson: String) = RecoveryCodeDenied(rawJson)
        }
    }

    /**
     * Sent by the Host when it cannot supply a recovery code (e.g. no account exists and creation
     * failed). The Joiner must abort the session on receiving this.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeUnavailable private constructor(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_unavailable"

            fun create() = RecoveryCodeUnavailable(buildMessageJson(TYPE))

            fun fromJson(rawJson: String) = RecoveryCodeUnavailable(rawJson)
        }
    }

    /**
     * Sent by the Host carrying the actual recovery code. Terminal message of a successful pairing
     * exchange.
     *
     * @param recoveryCode base64url-encoded recovery code payload.
     */
    @ConsistentCopyVisibility
    data class RecoveryCodeResponse private constructor(
        override val rawJson: String,
        val recoveryCode: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            const val TYPE = "recovery_code_response"
            private const val FIELD_RECOVERY_CODE = "recovery_code"

            fun create(recoveryCode: String) = RecoveryCodeResponse(
                rawJson = buildMessageJson(TYPE) { put(FIELD_RECOVERY_CODE, recoveryCode) },
                recoveryCode = recoveryCode,
            )

            fun fromJson(rawJson: String): RecoveryCodeResponse {
                val json = JSONObject(rawJson)
                return RecoveryCodeResponse(
                    rawJson = rawJson,
                    recoveryCode = json.optString(FIELD_RECOVERY_CODE, ""),
                )
            }
        }
    }

    /**
     * Forward-compatibility variant: a message whose type field doesn't match any known
     * value. The SM drops these without changing state per the spec's forward-compat rule.
     *
     * Inbound only — we never send a type we don't know so there is no `create`.
     */
    @ConsistentCopyVisibility
    data class Unknown private constructor(
        override val rawJson: String,
        override val messageType: String,
    ) : ExchangeV2Message {
        /** Inert: we only ever receive these, so this is never written to an envelope. */
        override val protocolVersion get() = ExchangeProtocolVersion.V2_0

        companion object {
            fun fromJson(rawJson: String, messageType: String) = Unknown(rawJson, messageType)
        }
    }

    companion object {
        internal const val FIELD_TYPE = "type"
    }
}

private fun buildMessageJson(
    type: String,
    fields: JSONObject.() -> Unit = {},
): String = JSONObject().apply {
    put(ExchangeV2Message.FIELD_TYPE, type)
    fields()
}.toString()
