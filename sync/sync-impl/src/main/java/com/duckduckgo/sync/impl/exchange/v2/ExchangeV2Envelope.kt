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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.ExchangeEnvelope
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Wire-level envelope wrapping for relay messages. The outer object is `{version, payload}`
 * with [version] unencrypted (used for protocol version negotiation) and [payload] a JWE
 * compact string carrying the encrypted message JSON.
 *
 * Spec: Transport TD (Asana 1214486492252757) §Message Envelope + §Encryption.
 *  - alg = RSA-OAEP-256 (wraps an ephemeral A256GCM key)
 *  - enc = A256GCM
 *  - kid = sender's channel_id
 */
interface ExchangeV2Envelope {

    /**
     * Build an outbound envelope encrypting [message] to the recipient's [peerPublicKeyBase64].
     * [senderChannelId] is written as the JWE `kid` header so the recipient knows the sender.
     */
    fun seal(message: ExchangeV2Message, peerPublicKeyBase64: String, senderChannelId: String): ExchangeEnvelope

    /**
     * Decrypt an inbound envelope with our ephemeral private key, returning the inner message JSON.
     *
     * @throws EnvelopeVersionTooNew if the envelope's major version is higher than ours.
     */
    fun open(envelope: ExchangeEnvelope, ownPrivateKeyBase64: String): String
}

/** Thrown when an envelope requires a protocol version we don't support. */
class EnvelopeVersionTooNew(val version: ExchangeProtocolVersion) : RuntimeException(
    "Envelope requires protocol v$version; we only support up to v${ExchangeProtocolVersion.V2.MAJOR}",
)

/**
 * Thrown when an envelope's payload can't be decrypted or parsed. Permanent (the same bytes
 * fail identically on retry), so the runner treats it as terminal rather than retrying.
 */
class EnvelopeDecryptFailure(val seq: Int, cause: Throwable) : RuntimeException(
    "Failed to decrypt envelope seq=$seq: ${cause.message}",
    cause,
)

@ContributesBinding(AppScope::class)
class RealExchangeV2Envelope @Inject constructor(
    private val jweCrypto: SyncJweCrypto,
) : ExchangeV2Envelope {

    override fun seal(message: ExchangeV2Message, peerPublicKeyBase64: String, senderChannelId: String): ExchangeEnvelope {
        val jwe = jweCrypto.jweEncryptRsaOaep(
            plaintext = message.rawJson.toByteArray(Charsets.UTF_8),
            recipientPublicKeyBase64 = peerPublicKeyBase64,
            kid = senderChannelId,
        )
        return ExchangeEnvelope(version = message.protocolVersion.toString(), payload = jwe)
    }

    override fun open(envelope: ExchangeEnvelope, ownPrivateKeyBase64: String): String {
        val protocol = ExchangeProtocolVersion.parse(envelope.version).getOrElse {
            throw IllegalArgumentException("Malformed envelope version: ${envelope.version}")
        }
        return when (protocol) {
            is ExchangeProtocolVersion.V2 -> {
                val decrypted = jweCrypto.jweDecryptRsaOaep(envelope.payload, ownPrivateKeyBase64)
                String(decrypted, Charsets.UTF_8)
            }

            is ExchangeProtocolVersion.V1 -> {
                throw IllegalArgumentException("Obsolete envelope version $protocol")
            }

            else -> {
                throw EnvelopeVersionTooNew(protocol)
            }
        }
    }
}
