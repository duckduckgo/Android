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
 * Wire messages exchanged over the v2 /sync/v2/exchange/{channelId} channel.
 *
 * Spec: Asana 1215056232572322 (Exchange V2 Message Sequence State Machine).
 * Each subtype carries its raw JSON for replay/inspection in the dev screen.
 */
sealed interface ExchangeV2Message {
    val rawJson: String
    val messageType: String

    /**
     * Sent by the Scanner to the Presenter's relay channel to open the session.
     *
     * @param channelId Scanner's own relay channel ID — the Presenter learns this here and uses
     *  it as the address for replies.
     * @param publicKey Scanner's session public key (base64url-encoded SPKI DER); used to
     *  encrypt all subsequent messages addressed to the Scanner.
     * @param version Sender's minimum protocol version (e.g. "2", "2.1").
     */
    data class Hello(
        override val rawJson: String,
        val channelId: String = "",
        val publicKey: String = "",
        val version: String = "2",
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "hello"
        }
    }

    /**
     * Sender has a sync account. Carries [userId] for same-account detection, [kind] (ddg
     * or 3party) for cross-kind role election, and human-readable [name] for the user UI.
     */
    data class RecoveryCodeAvailable(
        override val rawJson: String,
        val userId: String,
        val name: String,
        val kind: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_available"
        }
    }

    /**
     * Sender has no sync account. Carries [kind] and [name]; user_id is implied null.
     */
    data class RecoveryCodeRequest(
        override val rawJson: String,
        val name: String,
        val kind: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_request"
        }
    }

    data class RecoveryCodeAwaitingConfirmation(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_awaiting_confirmation"
        }
    }

    data class RecoveryCodeConfirmed(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_confirmed"
        }
    }

    data class RecoveryCodeDenied(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_denied"
        }
    }

    data class RecoveryCodeUnavailable(
        override val rawJson: String,
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_unavailable"
        }
    }

    /**
     * Sent by the Host carrying the actual recovery code. Terminal happy-path message.
     *
     * @param recoveryCode base64url-encoded recovery code payload.
     */
    data class RecoveryCodeResponse(
        override val rawJson: String,
        val recoveryCode: String = "",
    ) : ExchangeV2Message {
        override val messageType: String = TYPE

        companion object {
            const val TYPE = "recovery_code_response"
        }
    }

    /**
     * Forward-compatibility variant: a message whose type field doesn't match any known
     * value. The SM drops these without changing state per the spec's forward-compat rule.
     */
    data class Unknown(
        override val rawJson: String,
        override val messageType: String,
    ) : ExchangeV2Message
}
