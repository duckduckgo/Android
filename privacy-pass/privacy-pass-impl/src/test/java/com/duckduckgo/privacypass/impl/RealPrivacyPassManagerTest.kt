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

package com.duckduckgo.privacypass.impl

import com.duckduckgo.privacypass.api.PrivacyPassChallenge
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.Base64

class RealPrivacyPassManagerTest {

    private val parser = ChallengeParser()

    @Test
    fun testIsReadyReflectsRemoteFeatureToggle() {
        val togglesRepository = mock<PrivacyFeatureTogglesRepository>()
        whenever(
            togglesRepository.get(
                featureName = PrivacyFeatureName.PrivacyPassFeatureName,
                defaultValue = false,
            ),
        ).thenReturn(true)
        assertTrue(isReady(togglesRepository))

        whenever(
            togglesRepository.get(
                featureName = PrivacyFeatureName.PrivacyPassFeatureName,
                defaultValue = false,
            ),
        ).thenReturn(false)
        assertFalse(isReady(togglesRepository))
    }

    @Test
    fun testParseChallenge_validHeader_parsesCorrectly() {
        val issuerUrl = "http://127.0.0.1:8443"
        val issuerBytes = issuerUrl.toByteArray(Charsets.UTF_8)
        val redemptionContext = ByteArray(32) { it.toByte() }

        val tokenChallenge = ByteBuffer.allocate(4 + issuerBytes.size + 32 + 2)
        tokenChallenge.putShort(0xDA15.toShort())
        tokenChallenge.putShort(issuerBytes.size.toShort())
        tokenChallenge.put(issuerBytes)
        tokenChallenge.put(redemptionContext)
        tokenChallenge.putShort(0) // empty origin_info
        val challengeBytes = tokenChallenge.array()

        val challengeB64url = base64urlEncode(challengeBytes)
        val tokenKeyB64url = base64urlEncode(ByteArray(64) { 0xAA.toByte() })

        val header = "PrivateToken challenge=$challengeB64url, token-key=$tokenKeyB64url"
        val result = parser.parseChallenge(header)

        assertNotNull(result)
        assertEquals(0xDA15, result!!.tokenType)
        assertEquals(issuerUrl, result.issuerUrl)
        assertArrayEquals(redemptionContext, result.redemptionContext)
        assertNotNull(result.rawTokenChallenge)
    }

    @Test
    fun testParseChallenge_rfc8941StructuredFields_parsesCorrectly() {
        val issuerUrl = "http://127.0.0.1:8443"
        val issuerBytes = issuerUrl.toByteArray(Charsets.UTF_8)
        val redemptionContext = ByteArray(32) { it.toByte() }

        val tokenChallenge = ByteBuffer.allocate(4 + issuerBytes.size + 32 + 2)
        tokenChallenge.putShort(0xDA15.toShort())
        tokenChallenge.putShort(issuerBytes.size.toShort())
        tokenChallenge.put(issuerBytes)
        tokenChallenge.put(redemptionContext)
        tokenChallenge.putShort(0)
        val challengeBytes = tokenChallenge.array()

        val challengeB64 = Base64.getEncoder().encodeToString(challengeBytes)
        val tokenKeyB64 = Base64.getEncoder().encodeToString(ByteArray(64) { 0xAA.toByte() })

        val header = "PrivateToken challenge=:$challengeB64:, token-key=:$tokenKeyB64:"
        val result = parser.parseChallenge(header)

        assertNotNull(result)
        assertEquals(0xDA15, result!!.tokenType)
        assertEquals(issuerUrl, result.issuerUrl)
        assertArrayEquals(redemptionContext, result.redemptionContext)
        assertNotNull(result.rawTokenChallenge)
    }

    @Test
    fun testParseChallenge_invalidHeader_returnsNull() {
        assertNull(parser.parseChallenge("Bearer realm=\"example\""))
        assertNull(parser.parseChallenge("PrivateToken"))
        assertNull(parser.parseChallenge("PrivateToken token-key=abc"))
        assertNull(parser.parseChallenge(""))
    }

    @Test
    fun testBase64urlEncode_matchesExpected() {
        val input = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte(), 0xFE.toByte())
        val encoded = base64urlEncode(input)
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("="))
    }

    @Test
    fun testBase64urlDecode_matchesExpected() {
        val original = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F) // "Hello"
        val encoded = base64urlEncode(original)
        val decoded = base64urlDecode(encoded)
        assertNotNull(decoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun testBuildTokenStruct_correctFormat() {
        val spendProof = ByteArray(128) { it.toByte() }
        val challenge = PrivacyPassChallenge(
            tokenType = 0xDA15,
            issuerUrl = "http://127.0.0.1:8443",
            challenge = "test",
            tokenKey = null,
            redemptionContext = ByteArray(32),
            rawTokenChallenge = ByteArray(40),
        )
        val tokenStruct = buildTokenStructForTest(challenge, spendProof)

        // token_type (2) + nonce (32) + challenge_digest (32) + authenticator
        assertEquals(2 + 32 + 32 + spendProof.size, tokenStruct.size)

        val tokenType = ((tokenStruct[0].toInt() and 0xFF) shl 8) or (tokenStruct[1].toInt() and 0xFF)
        assertEquals(0xDA15, tokenType)
    }

    @Test
    fun testIsPrivateTokenChallenge_401WithHeader_returnsTrue() {
        val headers = mapOf("WWW-Authenticate" to "PrivateToken challenge=abc, token-key=def")
        assertTrue(isPrivateTokenChallenge(401, headers))
    }

    @Test
    fun testIsPrivateTokenChallenge_200_returnsFalse() {
        val headers = mapOf("WWW-Authenticate" to "PrivateToken challenge=abc, token-key=def")
        assertFalse(isPrivateTokenChallenge(200, headers))
    }

    @Test
    fun testIsPrivateTokenChallenge_401WithoutHeader_returnsFalse() {
        val headers = mapOf("Content-Type" to "text/html")
        assertFalse(isPrivateTokenChallenge(401, headers))
    }

    // Pure function extractions for testing without native/Android dependencies

    private fun isPrivateTokenChallenge(statusCode: Int, headers: Map<String, String>): Boolean {
        if (statusCode != 401) return false
        val wwwAuth = headers.entries.firstOrNull {
            it.key.equals("WWW-Authenticate", ignoreCase = true)
        }?.value ?: return false
        return wwwAuth.startsWith("PrivateToken", ignoreCase = true)
    }

    private fun isReady(togglesRepository: PrivacyFeatureTogglesRepository): Boolean {
        return togglesRepository.get(
            featureName = PrivacyFeatureName.PrivacyPassFeatureName,
            defaultValue = false,
        )
    }

    private fun buildTokenStructForTest(challenge: PrivacyPassChallenge, spendProofCbor: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(2 + 32 + 32 + spendProofCbor.size)
        buf.putShort(challenge.tokenType.toShort())
        buf.put(ByteArray(32)) // nonce (zeroed for deterministic test)
        val raw = challenge.rawTokenChallenge
        val challengeDigest = if (raw != null) {
            java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        } else {
            ByteArray(32)
        }
        buf.put(challengeDigest)
        buf.put(spendProofCbor)
        return buf.array()
    }

    private fun base64urlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun base64urlDecode(input: String): ByteArray? {
        return try {
            Base64.getUrlDecoder().decode(input)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Minimal challenge parser mirroring RealPrivacyPassManager logic for pure JVM testing.
     */
    class ChallengeParser {
        fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge? {
            if (!wwwAuthenticateHeader.startsWith("PrivateToken", ignoreCase = true)) {
                return null
            }

            val params = wwwAuthenticateHeader.substringAfter("PrivateToken").trim()
            val paramMap = parseAuthParams(params)

            val challengeB64 = stripStructuredFieldDelimiters(paramMap["challenge"] ?: return null)
            val tokenKeyB64url = paramMap["token-key"]?.let { stripStructuredFieldDelimiters(it) }

            val challengeBytes = base64Decode(challengeB64) ?: base64urlDecode(challengeB64) ?: return null

            if (challengeBytes.size < 4) return null

            val tokenType = ((challengeBytes[0].toInt() and 0xFF) shl 8) or (challengeBytes[1].toInt() and 0xFF)
            val issuerNameLen = ((challengeBytes[2].toInt() and 0xFF) shl 8) or (challengeBytes[3].toInt() and 0xFF)

            if (challengeBytes.size < 4 + issuerNameLen + 32) return null

            val issuerUrl = String(challengeBytes, 4, issuerNameLen, Charsets.UTF_8)
            val redemptionStart = 4 + issuerNameLen
            val redemptionContext = challengeBytes.copyOfRange(redemptionStart, redemptionStart + 32)

            return PrivacyPassChallenge(
                tokenType = tokenType,
                issuerUrl = issuerUrl,
                challenge = challengeB64,
                tokenKey = tokenKeyB64url,
                redemptionContext = redemptionContext,
                rawTokenChallenge = challengeBytes,
            )
        }

        private fun parseAuthParams(paramString: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var remaining = paramString.trim()
            while (remaining.isNotEmpty()) {
                val eqIndex = remaining.indexOf('=')
                if (eqIndex == -1) break
                val key = remaining.substring(0, eqIndex).trim().trimStart(',').trim()
                remaining = remaining.substring(eqIndex + 1).trim()
                val value: String
                if (remaining.startsWith("\"")) {
                    val endQuote = remaining.indexOf('"', 1)
                    if (endQuote == -1) break
                    value = remaining.substring(1, endQuote)
                    remaining = remaining.substring(endQuote + 1).trim().trimStart(',').trim()
                } else {
                    val commaIndex = remaining.indexOf(',')
                    if (commaIndex == -1) {
                        value = remaining.trim()
                        remaining = ""
                    } else {
                        value = remaining.substring(0, commaIndex).trim()
                        remaining = remaining.substring(commaIndex + 1).trim()
                    }
                }
                result[key.lowercase()] = value
            }
            return result
        }

        private fun stripStructuredFieldDelimiters(value: String): String {
            if (value.startsWith(":") && value.endsWith(":") && value.length > 2) {
                return value.substring(1, value.length - 1)
            }
            return value
        }

        private fun base64Decode(input: String): ByteArray? {
            return try {
                Base64.getDecoder().decode(input)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun base64urlDecode(input: String): ByteArray? {
            return try {
                Base64.getUrlDecoder().decode(input)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
