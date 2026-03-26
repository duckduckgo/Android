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

import android.util.Base64
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacypass.api.PrivacyPassChallenge
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.duckduckgo.privacypass.api.PrivacyPassResult
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TOKEN_TYPE_ACT = 0xDA15
private const val PRIVATE_TOKEN_SCHEME = "PrivateToken"
private const val WWW_AUTHENTICATE = "WWW-Authenticate"

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyPassManager @Inject constructor(
    @PrivacyPassClient private val okHttpClient: OkHttpClient,
) : PrivacyPassManager {

    private val credentialStore = ConcurrentHashMap<String, StoredCredential>()

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
        logcat { "PrivacyPass: detected PrivateToken challenge for $originalUrl" }

        val challenge = parseChallenge(wwwAuthenticateHeader)
            ?: return PrivacyPassResult.Failure("Failed to parse WWW-Authenticate header")

        logcat { "PrivacyPass: parsed challenge — issuer=${challenge.issuerUrl}, tokenType=0x${challenge.tokenType.toString(16)}" }

        if (challenge.tokenType != TOKEN_TYPE_ACT) {
            return PrivacyPassResult.Failure(
                "Unsupported token type: 0x${challenge.tokenType.toString(16)}, expected 0x${TOKEN_TYPE_ACT.toString(16)}",
            )
        }

        val existingCredential = credentialStore[challenge.issuerUrl]
        if (existingCredential != null) {
            logcat { "PrivacyPass: found existing credential for ${challenge.issuerUrl}" }
            return createSpendProofAndAuthorize(challenge, existingCredential)
        }

        return runActProtocol(challenge)
    }

    override fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge? {
        if (!wwwAuthenticateHeader.startsWith(PRIVATE_TOKEN_SCHEME, ignoreCase = true)) {
            return null
        }

        val params = wwwAuthenticateHeader.substringAfter(PRIVATE_TOKEN_SCHEME).trim()
        val paramMap = parseAuthParams(params)

        val challenge = paramMap["challenge"] ?: return null
        val tokenKey = paramMap["token-key"] ?: return null
        val issuerUrl = paramMap["issuer"] ?: return null

        val tokenType = paramMap["token-type"]?.let { parseTokenType(it) } ?: TOKEN_TYPE_ACT

        return PrivacyPassChallenge(
            tokenType = tokenType,
            issuerUrl = issuerUrl,
            challenge = challenge,
            tokenKey = tokenKey,
        )
    }

    private fun runActProtocol(challenge: PrivacyPassChallenge): PrivacyPassResult {
        return try {
            logcat { "PrivacyPass: starting ACT protocol with issuer ${challenge.issuerUrl}" }

            val publicKey = fetchIssuerPublicKey(challenge.issuerUrl)
                ?: return PrivacyPassResult.Failure("Failed to fetch issuer public key")
            logcat { "PrivacyPass: fetched issuer public key (${publicKey.length} bytes)" }

            // TODO: In production, call Rust FFI via JNI here:
            //   val preIssuance = ActCore.preIssuance(publicKey, challenge.challenge)
            //   val issuanceRequest = preIssuance.encodedRequest()
            // For prototype, we construct a placeholder issuance request that signals
            // the server to use its own test flow.
            val issuanceRequestBody = buildPrototypeIssuanceRequest(challenge)

            val issuanceResponse = postIssuanceRequest(challenge.issuerUrl, issuanceRequestBody)
                ?: return PrivacyPassResult.Failure("Issuance request failed")
            logcat { "PrivacyPass: received issuance response" }

            // TODO: In production, call Rust FFI via JNI here:
            //   val credential = ActCore.processIssuanceResponse(preIssuance, issuanceResponse)
            val credential = StoredCredential(
                issuerUrl = challenge.issuerUrl,
                issuanceResponseBase64 = issuanceResponse,
                publicKeyBase64 = publicKey,
            )
            credentialStore[challenge.issuerUrl] = credential
            logcat { "PrivacyPass: stored credential for ${challenge.issuerUrl}" }

            createSpendProofAndAuthorize(challenge, credential)
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: ACT protocol failed: ${e.message}" }
            PrivacyPassResult.Failure("ACT protocol error: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createSpendProofAndAuthorize(
        challenge: PrivacyPassChallenge,
        credential: StoredCredential,
    ): PrivacyPassResult {
        // TODO: In production, call Rust FFI via JNI here:
        //   val spendProof = ActCore.proveSpend(credential.nativeHandle, challenge.challenge)
        //   val tokenBase64 = Base64.encodeToString(spendProof, Base64.NO_WRAP)

        val spendResult = postSpendProof(credential)
        if (spendResult != null) {
            logcat { "PrivacyPass: spend proof accepted by issuer" }
            val authHeader = "$PRIVATE_TOKEN_SCHEME token=$spendResult"
            return PrivacyPassResult.Success(authHeader)
        }

        // If the server-side spend flow isn't available, construct the header
        // from the stored issuance data as a best-effort prototype fallback.
        val fallbackToken = credential.issuanceResponseBase64
        logcat { "PrivacyPass: using issuance response as prototype fallback token" }
        val authHeader = "$PRIVATE_TOKEN_SCHEME token=$fallbackToken"
        return PrivacyPassResult.Success(authHeader)
    }

    private fun fetchIssuerPublicKey(issuerUrl: String): String? {
        val url = "${issuerUrl.trimEnd('/')}/public-key"
        logcat { "PrivacyPass: GET $url" }

        return try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: public-key fetch failed: HTTP ${response.code}" }
                return null
            }
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            json.optString("public_key").takeIf { it.isNotEmpty() }
                ?: json.optString("publicKey").takeIf { it.isNotEmpty() }
                ?: body
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: public-key fetch error: ${e.message}" }
            null
        }
    }

    private fun buildPrototypeIssuanceRequest(challenge: PrivacyPassChallenge): ByteArray {
        // TODO: In production this would be a CBOR-encoded IssuanceRequest
        // generated by the Rust act-core library via JNI.
        // For prototype, send JSON that the test server can understand.
        val json = JSONObject().apply {
            put("token_type", challenge.tokenType)
            put("challenge", challenge.challenge)
            put("token_key", challenge.tokenKey)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun postIssuanceRequest(issuerUrl: String, body: ByteArray): String? {
        val url = "${issuerUrl.trimEnd('/')}/token-request"
        logcat { "PrivacyPass: POST $url (${body.size} bytes)" }

        return try {
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(mediaType))
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: token-request failed: HTTP ${response.code}" }
                return null
            }
            val responseBody = response.body?.string() ?: return null

            val json = JSONObject(responseBody)
            json.optString("token_response").takeIf { it.isNotEmpty() }
                ?: json.optString("tokenResponse").takeIf { it.isNotEmpty() }
                ?: Base64.encodeToString(responseBody.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: token-request error: ${e.message}" }
            null
        }
    }

    private fun postSpendProof(credential: StoredCredential): String? {
        val url = "${credential.issuerUrl.trimEnd('/')}/token-spend"
        logcat { "PrivacyPass: POST $url" }

        return try {
            // TODO: In production, the spend proof would be CBOR-encoded
            // output from ActCore.proveSpend(). For prototype, send the
            // issuance response back as a placeholder.
            val json = JSONObject().apply {
                put("issuance_response", credential.issuanceResponseBase64)
            }
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toByteArray().toRequestBody(mediaType))
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(ERROR) { "PrivacyPass: token-spend failed: HTTP ${response.code}" }
                return null
            }
            val responseBody = response.body?.string() ?: return null

            val responseJson = JSONObject(responseBody)
            responseJson.optString("token").takeIf { it.isNotEmpty() }
                ?: responseJson.optString("refund").takeIf { it.isNotEmpty() }
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

    private fun parseTokenType(value: String): Int {
        return try {
            if (value.startsWith("0x", ignoreCase = true)) {
                value.substring(2).toInt(16)
            } else {
                value.toInt()
            }
        } catch (_: NumberFormatException) {
            TOKEN_TYPE_ACT
        }
    }

    data class StoredCredential(
        val issuerUrl: String,
        val issuanceResponseBase64: String,
        val publicKeyBase64: String,
    )
}
