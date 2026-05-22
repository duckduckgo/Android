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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import io.jsonwebtoken.Jwts
import logcat.logcat
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

private const val JWE_CONTENT_TYPE_OCTET_STREAM = "application/octet-stream"

interface SyncJweCrypto {
    fun generateRsaKeyPair(): RsaKeyPair

    /**
     * JWE-encrypt content with a symmetric AES-256 key using direct encryption (alg=dir, enc=A256GCM).
     * Optional `kid` is written to the JWE header to identify which key was used to wrap the payload.
     */
    fun jweEncryptSymmetric(plaintext: ByteArray, symmetricKey: ByteArray, kid: String? = null): String

    /**
     * JWE-decrypt content that was encrypted with a symmetric AES-256 key (alg=dir, enc=A256GCM).
     */
    fun jweDecryptSymmetric(jweCompact: String, symmetricKey: ByteArray): ByteArray

    /**
     * JWE-encrypt content using RSA-OAEP-256 key wrap + A256GCM content encryption — the
     * format used by the v2 exchange relay envelope. [recipientPublicKeyBase64] is a base64url
     * SPKI DER public key (matches [generateRsaKeyPair]'s output). [kid] is written to the JWE
     * header — for the exchange protocol this is the sender's channel_id.
     */
    fun jweEncryptRsaOaep(plaintext: ByteArray, recipientPublicKeyBase64: String, kid: String): String

    /**
     * JWE-decrypt content encrypted with [jweEncryptRsaOaep]. [ownPrivateKeyBase64] is a
     * base64url PKCS#8 DER private key (matches [generateRsaKeyPair]'s output).
     */
    fun jweDecryptRsaOaep(jweCompact: String, ownPrivateKeyBase64: String): ByteArray

    /**
     * Extract RSA modulus (n) and exponent (e) from a SPKI-DER base64url public key.
     * Returns them as base64url strings ready for use in a JWK.
     */
    fun extractJwkComponents(publicKeyBase64: String): Pair<String, String>

    /**
     * HKDF-SHA-256 (RFC 5869), restricted to a single HKDF-Expand block ([outBytes] in 1..32).
     * Empty salt is permitted and treated as 32 zero bytes per §2.2. Callers needing more than 32
     * bytes of output should implement multi-block Expand explicitly.
     */
    fun hkdfSha256SingleBlock(input: ByteArray, salt: ByteArray, info: String, outBytes: Int = 32): ByteArray
}

data class RsaKeyPair(
    val publicKeyBase64: String,
    val privateKeyBase64: String,
)

@ContributesBinding(AppScope::class)
class RealSyncJweCrypto @Inject constructor() : SyncJweCrypto {

    override fun generateRsaKeyPair(): RsaKeyPair {
        logcat { "Sync-ScopedToken: generating RSA-$RSA_KEY_SIZE keypair" }
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()

        return RsaKeyPair(
            publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded),
            privateKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.private.encoded),
        )
    }

    override fun jweEncryptSymmetric(plaintext: ByteArray, symmetricKey: ByteArray, kid: String?): String {
        logcat { "Sync-ScopedToken: JWE encrypting with symmetric key (dir + A256GCM, kid=$kid)" }
        val secretKey = SecretKeySpec(symmetricKey, "AES")

        val builder = Jwts.builder()
        if (kid != null) builder.header().keyId(kid).and()
        return builder
            .content(plaintext, JWE_CONTENT_TYPE_OCTET_STREAM)
            .encryptWith(secretKey, Jwts.ENC.A256GCM)
            .compact()
    }

    override fun jweDecryptSymmetric(jweCompact: String, symmetricKey: ByteArray): ByteArray {
        logcat { "Sync-ScopedToken: JWE decrypting with symmetric key" }
        val secretKey = SecretKeySpec(symmetricKey, "AES")

        return Jwts.parser()
            .decryptWith(secretKey)
            .build()
            .parseEncryptedContent(jweCompact)
            .payload
    }

    /**
     * Manual JWE encode/decode rather than JJWT-driven so the wire format matches the
     * Encryption Algorithms TD exactly: header has just {alg, enc, kid} with no `cty`.
     * JJWT's [Jwts.parser] refuses to call `parseEncryptedContent` on a JWE without `cty`
     * (throws "Unexpected Claims JWE"), which broke interop with the Python reference
     * client. Manually parsing avoids that and gives byte-exact control over both directions.
     */
    override fun jweEncryptRsaOaep(plaintext: ByteArray, recipientPublicKeyBase64: String, kid: String): String {
        logcat { "Sync-ExchangeV2: JWE encrypting with RSA-OAEP-256 + A256GCM, kid=$kid" }
        val publicKey = decodePublicKey(recipientPublicKeyBase64)
        // Compact JSON, no whitespace — must be deterministic for AAD.
        val headerJson = JSONObject().apply {
            put("alg", "RSA-OAEP-256")
            put("enc", "A256GCM")
            put("kid", kid)
        }.toString()
        val headerB64 = b64UrlEncode(headerJson.toByteArray(Charsets.UTF_8))

        val aesKey = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }

        val aesCipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        aesCipher.updateAAD(headerB64.toByteArray(Charsets.US_ASCII))
        val combined = aesCipher.doFinal(plaintext)
        val ct = combined.copyOfRange(0, combined.size - GCM_TAG_BYTES)
        val tag = combined.copyOfRange(combined.size - GCM_TAG_BYTES, combined.size)

        val rsaCipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSha256Spec())
        val encKey = rsaCipher.doFinal(aesKey)

        return listOf(headerB64, b64UrlEncode(encKey), b64UrlEncode(iv), b64UrlEncode(ct), b64UrlEncode(tag))
            .joinToString(".")
    }

    override fun jweDecryptRsaOaep(jweCompact: String, ownPrivateKeyBase64: String): ByteArray {
        logcat { "Sync-ExchangeV2: JWE decrypting with RSA-OAEP-256 + A256GCM" }
        val privateKey = decodePrivateKey(ownPrivateKeyBase64)
        val parts = jweCompact.split('.')
        require(parts.size == 5) { "Malformed JWE compact: expected 5 parts, got ${parts.size}" }
        val headerB64 = parts[0]
        val encKey = Base64.getUrlDecoder().decode(parts[1])
        val iv = Base64.getUrlDecoder().decode(parts[2])
        val ct = Base64.getUrlDecoder().decode(parts[3])
        val tag = Base64.getUrlDecoder().decode(parts[4])

        val header = JSONObject(String(Base64.getUrlDecoder().decode(headerB64), Charsets.UTF_8))
        require(header.optString("alg") == "RSA-OAEP-256") { "Unsupported alg: ${header.optString("alg")}" }
        require(header.optString("enc") == "A256GCM") { "Unsupported enc: ${header.optString("enc")}" }

        val rsaCipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepSha256Spec())
        val aesKey = rsaCipher.doFinal(encKey)

        val aesCipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        aesCipher.updateAAD(headerB64.toByteArray(Charsets.US_ASCII))
        return aesCipher.doFinal(ct + tag)
    }

    private fun oaepSha256Spec(): OAEPParameterSpec =
        // Default MGF1 in OpenJDK is SHA-1; "RSA-OAEP-256" requires MGF1 with SHA-256, so we
        // pass it explicitly.
        OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    private fun b64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    override fun extractJwkComponents(publicKeyBase64: String): Pair<String, String> {
        val rsaPublicKey = decodePublicKey(publicKeyBase64) as RSAPublicKey
        val nBytes = rsaPublicKey.modulus.toByteArray().dropLeadingZero()
        val eBytes = rsaPublicKey.publicExponent.toByteArray().dropLeadingZero()
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return encoder.encodeToString(nBytes) to encoder.encodeToString(eBytes)
    }

    /**
     * BigInteger.toByteArray() may include a leading zero byte to indicate a positive sign.
     * JWK format expects the raw unsigned magnitude — strip the leading zero if present.
     */
    private fun ByteArray.dropLeadingZero(): ByteArray =
        if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this

    private fun decodePublicKey(base64: String): PublicKey {
        val keyBytes = Base64.getUrlDecoder().decode(base64)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    private fun decodePrivateKey(base64: String): PrivateKey {
        val keyBytes = Base64.getUrlDecoder().decode(base64)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    override fun hkdfSha256SingleBlock(input: ByteArray, salt: ByteArray, info: String, outBytes: Int): ByteArray {
        require(outBytes in 1..32) { "Single-iteration HKDF supports 1..32 bytes; got $outBytes" }
        // RFC 5869 §2.2: empty salt expands to HashLen zero bytes (32 for SHA-256).
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(input)
        // HKDF-Expand: single iteration since outBytes ≤ HashLen (32). T(1) = HMAC(PRK, info || 0x01)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(0x01.toByte())
        val t1 = mac.doFinal()
        return if (outBytes == t1.size) t1 else t1.copyOf(outBytes)
    }

    companion object {
        private const val RSA_KEY_SIZE = 2048
        private const val AES_KEY_BYTES = 32 // 256-bit AES
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }
}
