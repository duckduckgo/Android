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

package com.duckduckgo.sync.impl.crypto

import com.duckduckgo.sync.impl.RsaJwk
import com.squareup.moshi.Moshi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

/**
 * Pins the four cross-platform test vectors from the "Encryption Algorithms" TD
 * (Asana 1214802412121967) against [RealSyncJweCrypto]. Each test maps 1:1 to a
 * `testN()` block in the TD's reference JS, so a future change to the algorithm
 * surfaces as a named failure here.
 *
 * Decrypt vectors are the strongest cross-platform guarantee: they prove Android
 * can read envelopes produced by the FE reference impl byte-for-byte.
 */
@Suppress("ktlint:standard:max-line-length", "SameParameterValue")
class SyncJweCryptoTdVectorsTest {

    private val crypto = RealSyncJweCrypto()

    // ----- TD test fixtures (shared across all vectors) -----------------------------------------

    private val userId = "e2dd518f-7a8b-45f1-8478-d745b9173add"
    private val secretB64Url = "rUzlGqLLlbonAC_zIeh1nrCmuDsDAn6UooUUDz-6x3o"
    private val saltBytes = userId.toByteArray(Charsets.UTF_8)
    private val secretBytes = base64UrlDecode(secretB64Url)

    // ----- TD test1: hashed password (HKDF info="Password") --------------------------------------

    @Test
    fun tdTest1_hkdfHashedPasswordMatchesReferenceImpl() {
        // HKDF-SHA-256(secret_bytes, salt=user_id_utf8, info="Password", 32) → base64url
        val out = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Password", 32)
        val outB64u = base64UrlEncode(out)
        assertEquals("k30hxpUXY9SZIpHc9xrAs-VhPbX67euD0BBAaPQCdis", outB64u)
    }

    // ----- TD test2: MEK derivation (HKDF info="Main Key") ---------------------------------------

    @Test
    fun tdTest2_hkdfMekMatchesReferenceImpl() {
        // HKDF-SHA-256(secret_bytes, salt=user_id_utf8, info="Main Key", 32) → base64url
        val mek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val mekB64u = base64UrlEncode(mek)
        assertEquals("i_7OWhiM4eR6nR12wKiF97pEl0q7tMFOr5dqeaepGaw", mekB64u)
    }

    // ----- TD test3: encrypted_3party_credential -------------------------------------------------

    @Test
    fun tdTest3_decryptFeProducedEncrypted3partyCredentialReturnsThirdPartySecret() {
        // The TD's reference impl encrypts a 3rd-party `secret` with the DDG MEK and a fixed IV.
        // We pin Android's ability to *decrypt* the resulting envelope byte-for-byte — the
        // strongest interop guarantee.
        val ddgMek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val expectedThirdPartySecret = "xlSU-OpN1VNaqMUTk61yq1CcFm8u2obL0jKQgpiP7YQ"
        val tdEnvelope =
            "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoiZGRnIn0..DEJLNnQw_F2048HD" +
                ".HVKsZje_tO7CjFEv_ySAVAOPUVtS7sNra8xIPMJ9HcM1DWhhvboMTUzQ6Q.7sczh6B5YE26ciE3P9aX7w"

        val decrypted = String(crypto.jweDecryptSymmetric(tdEnvelope, ddgMek), Charsets.UTF_8)

        assertEquals(expectedThirdPartySecret, decrypted)
    }

    @Test
    fun tdTest3_androidEncryptThenDecryptRoundTripsThroughDdgMek() {
        // Encryption itself is non-deterministic (random IV), so we round-trip rather than pin
        // exact bytes. Verifies our header/AAD/segment ordering matches what jweDecryptSymmetric
        // can re-parse.
        val ddgMek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val plaintext = "xlSU-OpN1VNaqMUTk61yq1CcFm8u2obL0jKQgpiP7YQ"

        val envelope = crypto.jweEncryptSymmetric(plaintext.toByteArray(Charsets.UTF_8), ddgMek, kid = "ddg")
        val decrypted = String(crypto.jweDecryptSymmetric(envelope, ddgMek), Charsets.UTF_8)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun tdTest3_envelopeHeaderHasExpectedFields() {
        // Header MUST be {"alg":"dir","enc":"A256GCM","kid":<kid>}. The TD's reference impl
        // produces the keys in that order; any reordering breaks AAD (header bytes are AAD).
        val ddgMek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val envelope = crypto.jweEncryptSymmetric("payload".toByteArray(), ddgMek, kid = "ddg")
        val headerB64u = envelope.substringBefore('.')
        val headerJson = String(Base64.getUrlDecoder().decode(headerB64u), Charsets.UTF_8)
        val header = JSONObject(headerJson)
        assertEquals("dir", header.getString("alg"))
        assertEquals("A256GCM", header.getString("enc"))
        assertEquals("ddg", header.getString("kid"))
        // 5 dot-separated segments, segment[1] empty (no per-recipient key for alg=dir)
        val segments = envelope.split('.')
        assertEquals(5, segments.size)
        assertEquals("", segments[1])
    }

    // ----- TD test4: encrypted_private_key (3party-side KEK wrapping) ----------------------------

    @Test
    fun tdTest4_decryptFeProduced3partyEncryptedPrivateKeyReturnsPkcs8Bytes() {
        // TD §"How do we encrypt KEKs using MEKs" — 3party-side `encrypted_private_key` is a JWE
        // compact envelope with kid="3party", AES-256-GCM, key = MEK derived from the 3party
        // credential's secret. Plaintext is the RSA private key in PKCS8 DER form.
        //
        // TD test4 uses the SAME MEK as test3 (both derived from `secret`). The "3party" kid is
        // semantic — both ddg and 3party MEKs are derived identically from their respective
        // recovery codes' secrets via HKDF(secret, salt=user_id, info="Main Key").
        val mek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val expectedPrivateKeyPkcs8B64Std =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDJ9MeOg55hLDc/wsWqB8H9vLNRWjE0y/Xc/oOzBkWIBzdcRZzB7h/C4JGjKPjQcjOqfcm1mfFY3aF9bP/jH70VKi++KaFDgKqDECcXsdXg6IhIvywEh/Tts39Mz3oKCndzGR+DjoCftcO4j7k1rdiIh9p6CwGh6feQMqXoGw0loKFEiY0yIehpu+Fk3Rprpy4DcgApZkKTTroOEh1Bq5uYtIzR40qMyuYRd0mRcx/BpmUBfM9GDtzSehXdMGKGLOVEWFYHq5Pi7P55N7943/8DBSH904L+w7G3Vi/zmTFL8XUOnqVMIkq5uBTteTkWP9TMMNedgyyzKNExdPQ/6LdJAgMBAAECggEAAJkn4hcNXWpDchlSIlnTfHHAYOxp/V/4njY20tuFvJaC6/VkjkeSOoZhZweplXBik3RV96+jaD12it9QLJ17g8Trm69HAbhBQCr8liQ9SMVBzEhMEU/B7IzEoH/syhzHaJOywhXUkD4JroRFVMjRQS/0BoQcrGiOuZcw5MRdfRwXtAHi07rfwCexIuQ8zDLoMSFnd1yLEv+jPqOwGWdD29w6FjowxC4VxLd96hp5GZ2vxN9sPZXVExFF9EfOgtDCCbFPMsZ78vEnaaCNSqbejOCac4pu8JH4bJiQF7ATqXEk9GTtCBmr3G4y8gMmsIbnb54ZZCB55l+0CKdLGDsHAQKBgQD290CGq736Z/wXH7VvGm+qWBw0zlFIvkX90jn/fABnE++Q5NjjgFyIlIUPrfK2D0jbFMYlOjfAgbX7Dr8/zXvswOxalN7+PIgoKRtxfqVT2tqVj9FLP1h1DlKLFFm9SIk8TAtMYzp9Ozpm6cP4YXspERDuqo8Hhoa5ILN6rbXESQKBgQDRWAci2z+5WJphi0ZlF/ECQvVuuiAdw5zeRkACNINxAm/3fLcLmzVJhkwXUgddBaPQn8eT0hRNBcmgEWbtx36ce7Yp5B4rwfxSQIFC2TqK3hKQMhDxjrZNK93uWOFVgHLQumHAvYnrv758aoSbYEvfDIh3NSUOqzZNojuEIAVbAQKBgQCj4aUG+LZjkVc+fQMny/Inprpo7DQSQnktmrBz8fROcnM5wjKOnSJKW8wEgJib6X6eKqXmFEDk1O5OwBV3IENI8yikXz+uk7qCc+zLHpBVGdiNANeQyGNJogxyUDnQmm6+/XNN6FbqvT/fBObPTtisgq+qwLGS+9kwxhtzoAwLSQKBgAUQ7ktHpwkjPckyh6eWprx5RltBodlWjItMg+wJvUyU1ITWvc9IGEgJOfougAMeSdKYq0nGgbtDcpevFCCY/VVoIQZugNRqQ2LyMK6fdy05JpXawFI4M+02LI7CE+Hv09d9SzRQ4e+UmlWEdmUUNYHWWc8YuCbcudmzHWGbLMYBAoGBAPVUeXIg2NzhMKwZ445H3y5PPNc5Km0ogtpGdLVkDTu5T89gIwxzDAdgPYcyzywDM1SX8llz+a1pYXkB+N0nO7+xJYiViJNOCvzP8vIu/hAzH0aqgJESrCmYjKThGkKBTlgr+qRh4fV1tFmhPed77jR5ez2PafWs7+UbQSBc4ES0"
        val tdEnvelope =
            "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoiM3BhcnR5In0..DEJLNnQw_F2048HD" +
                ".Vbz7jRjxxJD-3BZkCCFT92W4YSMm34SKKQlAy_S2fY4FZaEx28qs6bwHOzBvD5UjnicQnal0GdZxMiljyOv8IEFxym9N7zMLmtHXiRJ56pS1KKC2k-CIG3DPoeSdu5bt3njxvPwMATuvuTxNrcj015nZzxLwxpn0MS9iPc4rhQ-n6j1j4hI7HJ8E-nQheCmjxavh5nQx0McG99Hs5XCvIQwcP8UJJlDS0qtW_h0EPmMZpIt4CpJ1CQFPUmRBDrFHSg-3AAGHTYO6NRUF3Zn0ZdQrPr3RoJmiSpACC6uQbttRosU83AgY_P0wnyT1VHykMB3E1_w13HV2NMTJboetUWr4f2dCTT-SpnD-Dc-lIvNmkev9wkTCoBFAooLUjSwjp5qoG7wm58DyunPQaGn5iScmGOIQHQttrhr_50KFYSitRf2ukuqehvkI9_Vp3VKgNIfhNBGqWEFnKvG0I_A7c1X7gbZalukUYzOjem0BQ0-vOrjzFD3_euu-Lxe86ljow2LnzPlKa2kIguzrigh3m60eSPt_Jvke09R29aEHokG9mCVrYM6zmeOKC1JfVCegxAgH0muO8osC3MPt1obgDGpWfCEMKsdazj9Cbigilx8F5Qaw2tkLABaY0nEE_A-RvZg-2qYqkzPSFq0kAazyKkOJK6MdhpFBbc1eDz2PJ2EWMRTtn0snmQYUGDY6sw3jSbb-sghKzJxhvxOdoKf8UA7X0OwoQtzJTtbCcetoJ7gdyDgI--k0gJ4kBM6QxDgPEOrrTyKR-uGC6jxYSCn3y207lNAwJFzXHgmSE8ywy7ZHgbUHGyzG457bMmOyEv4BVqvUZ_U8KTr3vFVs9ZMLxgUONWBTjzt_eke5ttsDRG4KaqW2jKz3vT-W9CJn_-H8DCVZJBdOdVaxsHJKRT8aaAZ-1YlDkfjjcLGxfAcrq3cUipAXN8yl9SoMhObSwY_F2uveWR5LN2F2sCPuqJAkh_QT1iqxFYfPsEjgulossPDF5x3Mwd-AB2fsqrvvA28r0WwOGCPMow9U8-faRnxKGjEu9zkrd2IJH4epasvMI-04EPHS4TBq6QkwEYcSHH-31p5dteOuowOFuiDozFJt8cKJkhmvNKECMXFRIcSWmzv0PPw8gtviHrnFl-wnE4658VJ3hZrcVVOLDcryRfag0U3jJ4pqchIjUbdkby1Q5z7VYjGjgI_i9SxxWDJFs16eFgpinm0-LNNrfhLDKOMagKgAnTc8HRgPeFoqR7mVmnt0xLD8fGyZowygC0ma4kOwwy8DQjq8ECGUeGWGRCEP0DTkgdBOgwA-xreGa4Az993QbV8R7ALcYRqRpDLmvyryX3Fph1sPWRh7BS7TyxzJKslJnOYoo_DI7dtkZl1ne7RYVY5Dib6rg1NlKt87_54wxDqMZSvkb-rJSF8y15VPLrJdkFwHVAHDRG86Bl-quMAURA5c2SIO0ipdJruU3hlhzl8X87Uc3OIJby9J-TgQ9krtt3o8-oAg6VYsoTsGFynTDcpkRxgetVO5NCWpIZELwRnCvVfNAiQYRe1ZQoEwclpkXGOhBtNPaodJwtW2Dl8VS7hsd49G56BRPcPG4Sa3r_oiIdKdR3edVpGkkwKghuM9._2rwwf9ah1D54pMzhmR9fA"

        val decryptedBytes = crypto.jweDecryptSymmetric(tdEnvelope, mek)
        val actualB64Std = Base64.getEncoder().encodeToString(decryptedBytes)

        assertEquals(expectedPrivateKeyPkcs8B64Std, actualB64Std)
    }

    @Test
    fun tdTest4_envelopeHeaderHas3partyKid() {
        val mek = crypto.hkdfSha256SingleBlock(secretBytes, saltBytes, "Main Key", 32)
        val envelope = crypto.jweEncryptSymmetric("rawkey".toByteArray(), mek, kid = "3party")
        val header = JSONObject(String(Base64.getUrlDecoder().decode(envelope.substringBefore('.')), Charsets.UTF_8))
        assertEquals("3party", header.getString("kid"))
    }

    // ----- TD test4: public_key JWK shape --------------------------------------------------------

    @Test
    fun tdTest4_rsaJwkWireShapeMatchesReferenceImplPlusServerRequiredUse() {
        val expectedN = "yfTHjoOeYSw3P8LFqgfB_byzUVoxNMv13P6DswZFiAc3XEWcwe4fwuCRoyj40HIzqn3JtZnxWN2hfWz_4x" +
            "-9FSovvimhQ4CqgxAnF7HV4OiISL8sBIf07bN_TM96Cgp3cxkfg46An7XDuI-5Na3YiIfaegsBoen3kDKl6BsNJaCh" +
            "RImNMiHoabvhZN0aa6cuA3IAKWZCk066DhIdQaubmLSM0eNKjMrmEXdJkXMfwaZlAXzPRg7c0noV3TBihizlRFhWB6" +
            "uT4uz-eTe_eN__AwUh_dOC_sOxt1Yv85kxS_F1Dp6lTCJKubgU7Xk5Fj_UzDDXnYMssyjRMXT0P-i3SQ"

        val jwk = RsaJwk(n = expectedN, e = "AQAB")
        val moshi = Moshi.Builder().build()
        val json = JSONObject(moshi.adapter(RsaJwk::class.java).toJson(jwk))

        assertEquals("RSA-OAEP-256", json.getString("alg"))
        assertEquals("AQAB", json.getString("e"))
        assertEquals(true, json.getBoolean("ext"))
        val keyOps = json.getJSONArray("key_ops")
        assertEquals(1, keyOps.length())
        assertEquals("encrypt", keyOps.getString(0))
        assertEquals("RSA", json.getString("kty"))
        assertEquals(expectedN, json.getString("n"))
        assertEquals("enc", json.getString("use"))
        assertNotEquals("sig", json.getString("use"))
    }

    @Test
    fun tdTest4_rsaJwkDeserializesFromServerShapedJson() {
        val serverJwkJson = """
            {
              "alg":"RSA-OAEP-256",
              "e":"AQAB",
              "ext":true,
              "key_ops":["encrypt"],
              "kty":"RSA",
              "n":"yfTHjoOeYSw3",
              "use":"enc"
            }
        """.trimIndent()

        val moshi = Moshi.Builder().build()
        val parsed = moshi.adapter(RsaJwk::class.java).fromJson(serverJwkJson)!!

        assertEquals("RSA-OAEP-256", parsed.alg)
        assertEquals("AQAB", parsed.e)
        assertEquals(true, parsed.ext)
        assertEquals(listOf("encrypt"), parsed.keyOps)
        assertEquals("RSA", parsed.kty)
        assertEquals("yfTHjoOeYSw3", parsed.n)
        assertEquals("enc", parsed.use)
    }

    @Test
    fun tdTest4_rsaJwkRoundTripsThroughMoshi() {
        val originalN = "yfTHjoOeYSw3P8LFqgfB_byzUVoxNMv13P6DswZFiAc3XEWcwe4fwuCRoyj40HIzqn3JtZnxWN2hfWz_4x"
        val originalJson = """{"alg":"RSA-OAEP-256","e":"AQAB","ext":true,"key_ops":["encrypt"],"kty":"RSA","n":"$originalN","use":"enc"}"""

        val moshi = Moshi.Builder().build()
        val parsed = moshi.adapter(RsaJwk::class.java).fromJson(originalJson)!!
        val reserialized = JSONObject(moshi.adapter(RsaJwk::class.java).toJson(parsed))

        assertEquals("RSA-OAEP-256", reserialized.getString("alg"))
        assertEquals("AQAB", reserialized.getString("e"))
        assertEquals(true, reserialized.getBoolean("ext"))
        val keyOps = reserialized.getJSONArray("key_ops")
        assertEquals(1, keyOps.length())
        assertEquals("encrypt", keyOps.getString(0))
        assertEquals("RSA", reserialized.getString("kty"))
        assertEquals(originalN, reserialized.getString("n"))
        assertEquals("enc", reserialized.getString("use"))
    }

    // ----- helpers --------------------------------------------------------------------------------

    private fun base64UrlDecode(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
    private fun base64UrlEncode(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}
