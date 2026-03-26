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
    private val actCore = ActCoreWrapper()

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

        val challenge = paramMap["challenge"] ?: return null
        val issuerUrl = paramMap["issuer"] ?: return null
        val tokenKey = paramMap["token-key"]
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

            val publicKeyCbor = fetchIssuerPublicKey(challenge.issuerUrl)
                ?: return PrivacyPassResult.Failure("Failed to fetch issuer public key")
            logcat { "PrivacyPass: fetched issuer public key (${publicKeyCbor.size} bytes CBOR)" }

            val issuanceResult = actCore.performIssuance(publicKeyCbor) { requestCborBase64 ->
                postIssuanceRequest(challenge.issuerUrl, requestCborBase64)
            }
            logcat { "PrivacyPass: issuance complete, token ${issuanceResult.tokenCbor.size} bytes" }

            val credential = StoredCredential(
                issuerUrl = challenge.issuerUrl,
                tokenCbor = issuanceResult.tokenCbor,
                publicKeyCbor = issuanceResult.publicKeyCbor,
            )
            credentialStore[challenge.issuerUrl] = credential
            logcat { "PrivacyPass: stored credential for ${challenge.issuerUrl}" }

            spendAndAuthorize(challenge, credential)
        } catch (e: ActCoreException) {
            logcat(ERROR) { "PrivacyPass: ACT FFI error: ${e.message}" }
            PrivacyPassResult.Failure("ACT FFI error: ${e.message}")
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: ACT protocol failed: ${e.message}" }
            PrivacyPassResult.Failure("ACT protocol error: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun spendAndAuthorize(
        challenge: PrivacyPassChallenge,
        credential: StoredCredential,
    ): PrivacyPassResult {
        return try {
            val spendResult = actCore.spend(credential.tokenCbor)
            logcat { "PrivacyPass: spend proof generated (${spendResult.spendProofCbor.size} bytes)" }

            try {
                val spendProofBase64 = Base64.encodeToString(spendResult.spendProofCbor, Base64.NO_WRAP)
                val refundCborBase64 = postSpendProof(credential.issuerUrl, spendProofBase64)

                if (refundCborBase64 != null) {
                    val refundCbor = Base64.decode(refundCborBase64, Base64.DEFAULT)
                    val newTokenCbor = actCore.completeRefund(
                        spendResult.preRefundPtr,
                        spendResult.spendProofCbor,
                        refundCbor,
                        credential.publicKeyCbor,
                    )
                    credentialStore[credential.issuerUrl] = credential.copy(tokenCbor = newTokenCbor)
                    logcat { "PrivacyPass: refund complete, updated stored credential" }
                } else {
                    logcat { "PrivacyPass: no refund response, credential will not be refreshed" }
                    credentialStore.remove(credential.issuerUrl)
                }

                val authHeader = "$PRIVATE_TOKEN_SCHEME token=$spendProofBase64"
                PrivacyPassResult.Success(authHeader)
            } finally {
                actCore.freePreRefund(spendResult.preRefundPtr)
            }
        } catch (e: ActCoreException) {
            logcat(ERROR) { "PrivacyPass: spend FFI error: ${e.message}" }
            PrivacyPassResult.Failure("Spend FFI error: ${e.message}")
        } catch (e: Exception) {
            logcat(ERROR) { "PrivacyPass: spend failed: ${e.message}" }
            PrivacyPassResult.Failure("Spend error: ${e.message}")
        }
    }

    private fun fetchIssuerPublicKey(issuerUrl: String): ByteArray? {
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
            val cborBase64 = json.optString("cbor").takeIf { it.isNotEmpty() }
            if (cborBase64 != null) {
                return Base64.decode(cborBase64, Base64.DEFAULT)
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
        logcat { "PrivacyPass: POST $url" }

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
        logcat { "PrivacyPass: POST $url" }

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
        val tokenCbor: ByteArray,
        val publicKeyCbor: ByteArray,
    )
}
