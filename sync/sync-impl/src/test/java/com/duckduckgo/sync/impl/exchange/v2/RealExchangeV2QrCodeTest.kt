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

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealExchangeV2QrCodeTest {

    private val qrCode = RealExchangeV2QrCode()

    // ---- parse: LinkingV2 ----

    @Test fun `parse identifies a well-formed v2 linking URL`() {
        val url = qrCode.buildLinkingCode(channelId = "abc", publicKeyBase64Url = "pubkey", version = "2")

        val result = qrCode.parse(url)

        assertTrue(result is ExchangeV2CodeParseResult.LinkingV2)
        result as ExchangeV2CodeParseResult.LinkingV2
        assertEquals("abc", result.channelId)
        assertEquals("pubkey", result.publicKey)
        assertEquals("2", result.version)
    }

    @Test fun `parse tolerates leading whitespace and prefix text before the URL`() {
        val url = qrCode.buildLinkingCode("c", "k")
        val noisy = "   Linking code: $url"

        val result = qrCode.parse(noisy)

        assertTrue(result is ExchangeV2CodeParseResult.LinkingV2)
    }

    @Test fun `parse accepts a bare base64url v2 payload (no URL wrapper)`() {
        val bareB64 = encodeUrl("""{"version":"2","channel_id":"x","public_key":"k"}""")

        val result = qrCode.parse(bareB64)

        assertTrue(result is ExchangeV2CodeParseResult.LinkingV2)
    }

    // ---- parse: LinkingV1 ----

    @Test fun `parse routes legacy connect-shape codes to LinkingV1`() {
        val payload = """{"connect":{"device_id":"abc","secret_key":"sk"}}"""
        val url = "https://duckduckgo.com/sync/pairing/#&code=${encodeUrl(payload)}"

        val result = qrCode.parse(url)

        assertEquals(ExchangeV2CodeParseResult.LinkingV1, result)
    }

    // ---- parse: RecoveryCode ----

    @Test fun `parse detects v2 recovery codes by user_id+secret fields`() {
        val payload = """{"recovery":{"user_id":"u","secret":"s","cid":"ddg","v":"2.0"}}"""
        val bareB64 = encodeUrl(payload)

        val result = qrCode.parse(bareB64)

        assertTrue(result is ExchangeV2CodeParseResult.RecoveryCode)
        result as ExchangeV2CodeParseResult.RecoveryCode
        assertEquals("u", result.rawJson.getString("user_id"))
        assertEquals("ddg", result.rawJson.getString("cid"))
    }

    @Test fun `parse rejects v1 recovery codes (primary_key) so they fall through to legacy stack`() {
        // v1 recovery uses primary_key, not secret. ExchangeV2QrCode is the v2 entry point —
        // it should NOT claim v1 recovery codes; they need to fall through to parseSyncAuthCode.
        val payload = """{"recovery":{"primary_key":"pk","user_id":"u"}}"""
        val bareB64 = encodeUrl(payload)

        val result = qrCode.parse(bareB64)

        assertEquals(ExchangeV2CodeParseResult.Unknown, result)
    }

    // ---- parse: Unknown ----

    @Test fun `parse returns Unknown for empty input`() {
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse(""))
    }

    @Test fun `parse returns Unknown for non-base64 garbage`() {
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse("not a code at all !!!"))
    }

    @Test fun `parse returns Unknown for URL with no code fragment`() {
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse("https://example.com/no-fragment"))
    }

    @Test fun `parse returns Unknown when the base64 payload is not JSON`() {
        val bareB64 = encodeUrl("not json at all")
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse(bareB64))
    }

    @Test fun `parse returns Unknown for JSON with no recognised top-level keys`() {
        val bareB64 = encodeUrl("""{"something":"else"}""")
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse(bareB64))
    }

    @Test fun `parse requires version=2 (not just any version field)`() {
        // version=3 isn't ours yet; should not be LinkingV2. Falls through to Unknown for the parser
        // (the caller deals with version-upgrade UX separately).
        val bareB64 = encodeUrl("""{"version":"3","channel_id":"x","public_key":"k"}""")
        assertEquals(ExchangeV2CodeParseResult.Unknown, qrCode.parse(bareB64))
    }

    // ---- buildLinkingCode round-trip ----

    @Test fun `buildLinkingCode emits a URL parse can decode back to its inputs`() {
        val built = qrCode.buildLinkingCode(channelId = "chan-123", publicKeyBase64Url = "pubkey-456")

        val parsed = qrCode.parse(built) as ExchangeV2CodeParseResult.LinkingV2

        assertEquals("chan-123", parsed.channelId)
        assertEquals("pubkey-456", parsed.publicKey)
    }

    @Test fun `buildLinkingCode produces the documented URL shape`() {
        val built = qrCode.buildLinkingCode(channelId = "c", publicKeyBase64Url = "k")
        assertTrue("expected URL prefix, got: $built", built.startsWith("https://duckduckgo.com/sync/pairing/#&code2="))
    }

    @Test fun `buildLinkingCode honours custom version`() {
        val built = qrCode.buildLinkingCode("c", "k", version = "2.1")
        // 2.1 isn't decoded by us (parse looks for exact "2"), but the wire shape carries it through.
        val fragment = built.substringAfter("code2=")
        val decoded = Base64.decode(fragment, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        assertTrue(String(decoded).contains("\"version\":\"2.1\""))
    }

    private fun encodeUrl(s: String): String =
        Base64.encodeToString(
            s.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )

    @Suppress("unused")
    private fun ExchangeV2CodeParseResult.assertIs(): Unit = run {
        // helper hook for clarity in IDE; intentionally a no-op
        assertNull(null)
    }
}
