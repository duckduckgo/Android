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

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.duckduckgo.actcore.ActCoreNative
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacypass.api.PrivacyPassChallenge
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.duckduckgo.privacypass.api.PrivacyPassResult
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val ACT_ORG = "duckduckgo"
private const val ACT_SERVICE = "privacy-pass"
private const val ACT_DEPLOYMENT = "prototype"
private const val ACT_VERSION = "2026-03"

private const val TOKEN_TYPE_ACT = 0xDA15
private const val PRIVATE_TOKEN_SCHEME = "PrivateToken"
private const val WWW_AUTHENTICATE = "WWW-Authenticate"

class ActCoreException(message: String) : Exception(message)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyPassManager @Inject constructor(
    private val context: Context,
    @PrivacyPassClient private val okHttpClient: OkHttpClient,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
) : PrivacyPassManager {

    private val credentialStore = ConcurrentHashMap<String, StoredCredential>()
    private val native = ActCoreNative()

    private val prefs: SharedPreferences? by lazy {
        sharedPreferencesProvider.getEncryptedSharedPreferences(PREFS_NAME)
    }

    init {
        loadPersistedCredentials()
    }

    private fun ensureNativeReady() {
        ActCoreNative.init(context)
    }

    override fun isReady(): Boolean = privacyFeatureTogglesRepository.get(
        featureName = PrivacyFeatureName.PrivacyPassFeatureName,
        defaultValue = false,
    )

    override fun isPrivateTokenChallenge(statusCode: Int, headers: Map<String, String>): Boolean {
        if (statusCode != 401) return false
        val wwwAuth = headers.entries.firstOrNull {
            it.key.equals(WWW_AUTHENTICATE, ignoreCase = true)
        }?.value ?: return false
        return wwwAuth.startsWith(PRIVATE_TOKEN_SCHEME, ignoreCase = true)
    }

    override suspend fun handlePrivateTokenChallenge(
        originalUrl: String,
        wwwAuthenticateHeader: String,
    ): PrivacyPassResult {
        if (!isReady()) {
            return PrivacyPassResult.Failure("privacy pass disabled")
        }

        val challenge = parseChallenge(wwwAuthenticateHeader)
            ?: return PrivacyPassResult.Failure("Failed to parse WWW-Authenticate header")

        if (challenge.tokenType != TOKEN_TYPE_ACT) {
            return PrivacyPassResult.Failure(
                "Unsupported token type: 0x${challenge.tokenType.toString(16)}, expected 0x${TOKEN_TYPE_ACT.toString(16)}",
            )
        }

        val existingCredential = credentialStore[challenge.issuerUrl]
        if (existingCredential != null) {
            return spendAndAuthorize(challenge, existingCredential)
        }

        return runActProtocol(challenge)
    }

    override fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge? {
        if (!wwwAuthenticateHeader.startsWith(PRIVATE_TOKEN_SCHEME, ignoreCase = true)) {
            return null
        }

        val params = wwwAuthenticateHeader.substringAfter(PRIVATE_TOKEN_SCHEME).trim()
        val paramMap = parseAuthParams(params)

        val challengeB64url = stripStructuredFieldDelimiters(paramMap["challenge"] ?: return null)
        val tokenKeyB64url = paramMap["token-key"]?.let { stripStructuredFieldDelimiters(it) }

        val challengeBytes = base64Decode(challengeB64url) ?: base64urlDecode(challengeB64url) ?: return null

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
            challenge = challengeB64url,
            tokenKey = tokenKeyB64url,
            redemptionContext = redemptionContext,
            rawTokenChallenge = challengeBytes,
        )
    }

    private fun runActProtocol(challenge: PrivacyPassChallenge): PrivacyPassResult {
        return try {
            ensureNativeReady()

            val paramsId = nativeCreateParams()

            val tokenKey = challenge.tokenKey
            val publicKeyCborBase64 = if (tokenKey != null) {
                val pkBytes = base64Decode(tokenKey) ?: base64urlDecode(tokenKey)
                    ?: return PrivacyPassResult.Failure("Invalid token-key base64/base64url")
                Base64.encodeToString(pkBytes, Base64.NO_WRAP)
            } else {
                fetchIssuerPublicKeyCborBase64(challenge.issuerUrl)
                    ?: return PrivacyPassResult.Failure("Failed to fetch issuer public key")
            }

            val publicKeyId = nativeParsePublicKey(publicKeyCborBase64)

            val issuanceReqJson = parseNativeResult(native.createIssuanceRequest(paramsId))
            val preIssuanceId = issuanceReqJson.getLong("preIssuanceId")
            val requestCborBase64 = issuanceReqJson.getString("requestCborBase64")

            val responseCborBase64 = postIssuanceRequest(challenge.issuerUrl, requestCborBase64)
                ?: return PrivacyPassResult.Failure("Issuance POST request failed")

            val completeJson = parseNativeResult(
                native.completeIssuance(preIssuanceId, paramsId, publicKeyId, requestCborBase64, responseCborBase64),
            )
            val tokenCborBase64 = completeJson.getString("tokenCborBase64")

            val credential = StoredCredential(
                issuerUrl = challenge.issuerUrl,
                tokenCborBase64 = tokenCborBase64,
                publicKeyCborBase64 = publicKeyCborBase64,
            )
            credentialStore[challenge.issuerUrl] = credential
            persistCredential(credential)

            spendAndAuthorize(challenge, credential)
        } catch (e: ActCoreException) {
            logcat(ERROR) { "PrivacyPass: ACT FFI error: ${e.message}" }
            PrivacyPassResult.Failure("ACT FFI error: ${e.message}")
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: ACT protocol failed: ${e.message}" }
            PrivacyPassResult.Failure("ACT protocol error: ${e.message}")
        }
    }

    private fun spendAndAuthorize(
        challenge: PrivacyPassChallenge,
        credential: StoredCredential,
    ): PrivacyPassResult {
        return try {
            ensureNativeReady()

            val paramsId = nativeCreateParams()
            val publicKeyId = nativeParsePublicKey(credential.publicKeyCborBase64)

            val loadJson = parseNativeResult(native.loadCreditToken(credential.tokenCborBase64))
            val creditTokenId = loadJson.getLong("creditTokenId")

            val spendJson = parseNativeResult(native.spend(creditTokenId, paramsId, 1L))
            val spendProofCborBase64 = spendJson.getString("spendProofCborBase64")
            val preRefundId = spendJson.getLong("preRefundId")

            val refundCborBase64 = postSpendProof(credential.issuerUrl, spendProofCborBase64)

            if (refundCborBase64 != null) {
                val refundJson = parseNativeResult(
                    native.completeRefund(preRefundId, paramsId, publicKeyId, spendProofCborBase64, refundCborBase64),
                )
                val newTokenCborBase64 = refundJson.getString("tokenCborBase64")
                val updatedCredential = credential.copy(tokenCborBase64 = newTokenCborBase64)
                credentialStore[credential.issuerUrl] = updatedCredential
                persistCredential(updatedCredential)
            } else {
                credentialStore.remove(credential.issuerUrl)
                removePersistedCredential(credential.issuerUrl)
            }

            val spendProofBytes = Base64.decode(spendProofCborBase64, Base64.DEFAULT)
            val tokenStruct = buildTokenStruct(challenge, spendProofBytes)
            val tokenB64 = Base64.encodeToString(tokenStruct, Base64.NO_WRAP)

            val authHeader = "$PRIVATE_TOKEN_SCHEME token=:$tokenB64:"
            PrivacyPassResult.Success(authHeader)
        } catch (e: ActCoreException) {
            logcat(ERROR) { "PrivacyPass: spend FFI error: ${e.message}" }
            PrivacyPassResult.Failure("Spend FFI error: ${e.message}")
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: spend failed: ${e.message}" }
            PrivacyPassResult.Failure("Spend error: ${e.message}")
        }
    }

    // Token struct per RFC 9577:
    //   token_type (2) | nonce (32) | challenge_digest (32) | token_key_id[Nid] | authenticator
    // For ACT token type 0xDA15, Nid=0 so token_key_id is empty.
    private fun buildTokenStruct(challenge: PrivacyPassChallenge, spendProofCbor: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(2 + 32 + 32 + spendProofCbor.size)
        buf.putShort(challenge.tokenType.toShort())

        val nonce = ByteArray(32)
        SecureRandom().nextBytes(nonce)
        buf.put(nonce)

        val challengeDigest = if (challenge.rawTokenChallenge != null) {
            MessageDigest.getInstance("SHA-256").digest(challenge.rawTokenChallenge)
        } else {
            ByteArray(32)
        }
        buf.put(challengeDigest)

        // token_key_id is empty for ACT (Nid=0)
        buf.put(spendProofCbor)
        return buf.array()
    }

    private fun nativeCreateParams(): Long {
        val json = parseNativeResult(native.createParams(ACT_ORG, ACT_SERVICE, ACT_DEPLOYMENT, ACT_VERSION))
        return json.getLong("paramsId")
    }

    private fun nativeParsePublicKey(cborBase64: String): Long {
        val json = parseNativeResult(native.parsePublicKey(cborBase64))
        return json.getLong("publicKeyId")
    }

    private fun parseNativeResult(jsonString: String): JSONObject {
        val json = JSONObject(jsonString)
        if (json.has("error")) {
            throw ActCoreException(json.getString("error"))
        }
        return json
    }

    private fun fetchIssuerPublicKeyCborBase64(issuerUrl: String): String? {
        val url = "${issuerUrl.trimEnd('/')}/public-key"

        return try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: public-key fetch failed: HTTP ${response.code}" }
                return null
            }
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val cborBase64 = json.optString("cbor").takeIf { it.isNotEmpty() }
            if (cborBase64 != null) {
                return cborBase64
            }

            logcat(ERROR) { "PrivacyPass: public-key response missing 'cbor' field" }
            null
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: public-key fetch error: ${e.message}" }
            null
        }
    }

    private fun postIssuanceRequest(issuerUrl: String, requestCborBase64: String): String? {
        val url = "${issuerUrl.trimEnd('/')}/token-request"

        return try {
            val json = JSONObject().apply { put("cbor", requestCborBase64) }
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toByteArray(Charsets.UTF_8).toRequestBody(mediaType))
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: token-request failed: HTTP ${response.code}" }
                return null
            }
            val responseBody = response.body?.string() ?: return null

            val responseJson = JSONObject(responseBody)
            responseJson.optString("cbor").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: token-request error: ${e.message}" }
            null
        }
    }

    private fun postSpendProof(issuerUrl: String, spendProofCborBase64: String): String? {
        val url = "${issuerUrl.trimEnd('/')}/token-spend"

        return try {
            val json = JSONObject().apply { put("cbor", spendProofCborBase64) }
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toByteArray(Charsets.UTF_8).toRequestBody(mediaType))
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: token-spend failed: HTTP ${response.code}" }
                return null
            }
            val responseBody = response.body?.string() ?: return null

            val responseJson = JSONObject(responseBody)
            responseJson.optString("cbor").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: token-spend error: ${e.message}" }
            null
        }
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
            Base64.decode(input, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun base64urlDecode(input: String): ByteArray? {
        return try {
            Base64.decode(input, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun base64urlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // MARK: - Credential persistence

    private fun persistCredential(credential: StoredCredential) {
        try {
            prefs?.edit()
                ?.putString("${PREF_TOKEN_PREFIX}${credential.issuerUrl}", credential.tokenCborBase64)
                ?.putString("${PREF_PUBKEY_PREFIX}${credential.issuerUrl}", credential.publicKeyCborBase64)
                ?.apply()
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: failed to persist credential: ${e.message}" }
        }
    }

    private fun removePersistedCredential(issuerUrl: String) {
        try {
            prefs?.edit()
                ?.remove("${PREF_TOKEN_PREFIX}$issuerUrl")
                ?.remove("${PREF_PUBKEY_PREFIX}$issuerUrl")
                ?.apply()
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: failed to remove persisted credential: ${e.message}" }
        }
    }

    private fun loadPersistedCredentials() {
        try {
            val allPrefs = prefs?.all ?: return
            val tokenKeys = allPrefs.keys.filter { it.startsWith(PREF_TOKEN_PREFIX) }

            for (tokenKey in tokenKeys) {
                val issuerUrl = tokenKey.removePrefix(PREF_TOKEN_PREFIX)
                val tokenCbor = allPrefs[tokenKey] as? String ?: continue
                val pubkeyCbor = allPrefs["${PREF_PUBKEY_PREFIX}$issuerUrl"] as? String ?: continue

                credentialStore[issuerUrl] = StoredCredential(
                    issuerUrl = issuerUrl,
                    tokenCborBase64 = tokenCbor,
                    publicKeyCborBase64 = pubkeyCbor,
                )
            }
            if (credentialStore.isNotEmpty()) {
                logcat { "PrivacyPass: loaded ${credentialStore.size} persisted credential(s)" }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: failed to load persisted credentials: ${e.message}" }
        }
    }

    data class StoredCredential(
        val issuerUrl: String,
        val tokenCborBase64: String,
        val publicKeyCborBase64: String,
    )

    companion object {
        private const val PREFS_NAME = "privacy_pass_credentials"
        private const val PREF_TOKEN_PREFIX = "credential_"
        private const val PREF_PUBKEY_PREFIX = "pubkey_"
    }
}
