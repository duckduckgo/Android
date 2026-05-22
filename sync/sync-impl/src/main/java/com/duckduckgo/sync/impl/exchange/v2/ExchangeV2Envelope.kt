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
     * Build an outbound envelope. [messageJson] is the inner message JSON (e.g. produced from
     * one of the [ExchangeV2Message] subtypes). [peerPublicKeyBase64] is the recipient's
     * session public key (from their QR code or hello). [senderChannelId] is written as the
     * JWE `kid` header so the recipient knows which side sent the message.
     */
    fun seal(messageJson: String, peerPublicKeyBase64: String, senderChannelId: String): ExchangeEnvelope

    /**
     * Decrypt an inbound envelope using our own ephemeral private key. Returns the inner
     * message JSON string.
     *
     * @throws EnvelopeVersionTooNew if the envelope's `version` field has a higher major
     *  than ours.
     */
    fun open(envelope: ExchangeEnvelope, ownPrivateKeyBase64: String): String
}

/** Thrown when an envelope requires a protocol version we don't support. */
class EnvelopeVersionTooNew(val version: String) : RuntimeException(
    "Envelope requires protocol v$version; we only support v$OUR_MAJOR",
)

/**
 * Thrown when an envelope's payload can't be decrypted or parsed (bad key, malformed JWE,
 * truncated bytes, etc.). Unlike a transient HTTP error this is permanent — the same bytes
 * will fail the same way next time — so the runner treats it as terminal rather than retrying.
 */
class EnvelopeDecryptFailure(val seq: Int, cause: Throwable) : RuntimeException(
    "Failed to decrypt envelope seq=$seq: ${cause.message}",
    cause,
)

const val OUR_MAJOR: Int = 2
const val OUR_VERSION_STRING: String = "2"

@ContributesBinding(AppScope::class)
class RealExchangeV2Envelope @Inject constructor(
    private val jweCrypto: SyncJweCrypto,
) : ExchangeV2Envelope {

    override fun seal(messageJson: String, peerPublicKeyBase64: String, senderChannelId: String): ExchangeEnvelope {
        val jwe = jweCrypto.jweEncryptRsaOaep(
            plaintext = messageJson.toByteArray(Charsets.UTF_8),
            recipientPublicKeyBase64 = peerPublicKeyBase64,
            kid = senderChannelId,
        )
        return ExchangeEnvelope(version = OUR_VERSION_STRING, payload = jwe)
    }

    override fun open(envelope: ExchangeEnvelope, ownPrivateKeyBase64: String): String {
        val major = parseMajor(envelope.version)
        if (major > OUR_MAJOR) throw EnvelopeVersionTooNew(envelope.version)
        if (major < OUR_MAJOR) throw IllegalArgumentException("Obsolete envelope version ${envelope.version}")
        val decrypted = jweCrypto.jweDecryptRsaOaep(envelope.payload, ownPrivateKeyBase64)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun parseMajor(version: String): Int {
        val majorPart = version.substringBefore('.')
        return majorPart.toIntOrNull()
            ?: throw IllegalArgumentException("Malformed envelope version: $version")
    }
}
