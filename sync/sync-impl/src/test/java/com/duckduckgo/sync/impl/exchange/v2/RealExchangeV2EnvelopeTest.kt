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

import com.duckduckgo.sync.impl.ExchangeEnvelope
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealExchangeV2EnvelopeTest {

    private val jweCrypto: SyncJweCrypto = mock()
    private val envelope = RealExchangeV2Envelope(jweCrypto)

    @Test fun `seal produces envelope with our version and JWE payload`() {
        whenever(jweCrypto.jweEncryptRsaOaep(any(), any(), any())).thenReturn("jwe-bytes")

        val result = envelope.seal(
            messageJson = """{"type":"hello"}""",
            peerPublicKeyBase64 = "peer-pub",
            senderChannelId = "our-channel",
        )

        assertEquals(OUR_VERSION_STRING, result.version)
        assertEquals("jwe-bytes", result.payload)
        verify(jweCrypto).jweEncryptRsaOaep(
            plaintext = """{"type":"hello"}""".toByteArray(Charsets.UTF_8),
            recipientPublicKeyBase64 = "peer-pub",
            kid = "our-channel",
        )
    }

    @Test fun `open decrypts payload and returns plaintext when version matches`() {
        whenever(jweCrypto.jweDecryptRsaOaep("payload-jwe", "our-priv"))
            .thenReturn("""{"type":"hello"}""".toByteArray(Charsets.UTF_8))

        val plain = envelope.open(ExchangeEnvelope(version = "2", payload = "payload-jwe"), "our-priv")

        assertEquals("""{"type":"hello"}""", plain)
    }

    @Test fun `open tolerates 2_x minor versions (forward-compat within major)`() {
        whenever(jweCrypto.jweDecryptRsaOaep(any(), any())).thenReturn("ok".toByteArray())

        val plain = envelope.open(ExchangeEnvelope(version = "2.5", payload = "p"), "k")

        assertEquals("ok", plain)
    }

    @Test fun `open throws EnvelopeVersionTooNew for major above ours`() {
        val ex = assertThrows(EnvelopeVersionTooNew::class.java) {
            envelope.open(ExchangeEnvelope(version = "3", payload = "anything"), "k")
        }
        assertEquals("3", ex.version)
    }

    @Test fun `open throws EnvelopeVersionTooNew even when minor is set`() {
        val ex = assertThrows(EnvelopeVersionTooNew::class.java) {
            envelope.open(ExchangeEnvelope(version = "4.2", payload = "anything"), "k")
        }
        assertEquals("4.2", ex.version)
    }

    @Test fun `open rejects obsolete (lower major) versions`() {
        assertThrows(IllegalArgumentException::class.java) {
            envelope.open(ExchangeEnvelope(version = "1", payload = "anything"), "k")
        }
    }

    @Test fun `open rejects malformed version strings`() {
        assertThrows(IllegalArgumentException::class.java) {
            envelope.open(ExchangeEnvelope(version = "two", payload = "p"), "k")
        }
    }

    @Test fun `open does not invoke jwe decrypt when version check fails`() {
        runCatching { envelope.open(ExchangeEnvelope("3", "p"), "k") }
        verify(jweCrypto, org.mockito.kotlin.never()).jweDecryptRsaOaep(any(), eq("k"))
    }
}
