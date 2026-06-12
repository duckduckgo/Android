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
import com.duckduckgo.sync.impl.RECOVERY_CODE_V2
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ThirdPartyRecoveryCode
import com.duckduckgo.sync.impl.ThirdPartyRecoveryCodeWrapper
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject

/**
 * Indirection over [SyncAccountRepository] so [ExchangeV2Runner] can fetch the recovery code for
 * a peer kind without depending on the wider repository surface. Returns the wire `rawCode` form,
 * not the QR form (kept distinct so a future spec tweak is a one-line change).
 */
interface RecoveryCodeProvider {

    /** Own DDG recovery code — used when peer is a `ddg` device joining our account. */
    fun getDdgRecoveryCode(): Result<String>

    /**
     * Own 3party access credential's recovery code — used when peer is a `3party` device.
     * Fails if the 3party credential doesn't exist yet; the runner then reports
     * `recovery_code_unavailable` to the peer.
     */
    fun getThirdPartyRecoveryCode(): Result<String>

    /**
     * Spec: Unified Algorithm §"Exchange Share Recovery Code" — *"If host has no account yet,
     * create it first."* No-op if already signed in; [Result.Error] if account creation fails.
     */
    fun createDdgAccountIfNeeded(): Result<Unit>

    /**
     * Spec: Unified Algorithm §"Exchange Share Recovery Code" — *"If this is ddg and peer is
     * 3party, if needed, extend the account."* No-op if a 3party credential already exists locally
     * ([SyncStore.scopedPassword] set); otherwise creates one. Failure surfaces as [Result.Error].
     */
    fun createThirdPartyCredentialIfNeeded(): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealRecoveryCodeProvider @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStore: com.duckduckgo.sync.store.SyncStore,
) : RecoveryCodeProvider {

    override fun createDdgAccountIfNeeded(): Result<Unit> {
        if (syncStore.userId != null) return Result.Success(Unit)
        return when (val r = syncAccountRepository.createAccount()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> r
        }
    }

    override fun createThirdPartyCredentialIfNeeded(): Result<Unit> {
        if (syncStore.scopedPassword != null) return Result.Success(Unit)
        return when (val r = syncAccountRepository.createThirdPartyCredential()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> r
        }
    }

    /**
     * Account stores v1 shape (`{recovery:{primary_key, user_id}}`); the v2 wire requires v2.0
     * shape (`{recovery:{user_id, secret, cid, v}}`). Convert before handing off to the runner.
     */
    override fun getDdgRecoveryCode(): Result<String> =
        when (val r = syncAccountRepository.getRecoveryCode()) {
            is Result.Success -> runCatching { toV2RecoveryCode(r.data.rawCode, cid = "ddg") }
                .fold(
                    onSuccess = { Result.Success(it) },
                    onFailure = { Result.Error(reason = "Failed to convert ddg recovery code to v2.0: ${it.message}") },
                )
            is Result.Error -> r
        }

    /** Already emits v2.0 shape — no conversion. */
    override fun getThirdPartyRecoveryCode(): Result<String> =
        when (val r = syncAccountRepository.getThirdPartyRecoveryCode()) {
            is Result.Success -> Result.Success(r.data.rawCode)
            is Result.Error -> r
        }

    private fun toV2RecoveryCode(v1Base64: String, cid: String): String {
        val decoded = decodePermissiveBase64(v1Base64)
        val v1 = JSONObject(String(decoded, Charsets.UTF_8)).getJSONObject("recovery")
        val userId = v1.getString("user_id")
        // v2 wire `secret` is base64url per spec 1214802412121967; re-encode the standard-base64 primary_key.
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(decodePermissiveBase64(v1.getString("primary_key")))
        val v2Json = recoveryCodeAdapter.toJson(
            ThirdPartyRecoveryCodeWrapper(
                recovery = ThirdPartyRecoveryCode(userId = userId, secret = secret, cid = cid, v = RECOVERY_CODE_V2),
            ),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(v2Json.toByteArray(Charsets.UTF_8))
    }

    private val recoveryCodeAdapter by lazy {
        Moshi.Builder().build().adapter(ThirdPartyRecoveryCodeWrapper::class.java)
    }

    /** Accept either standard or URL-safe base64 (with or without padding). */
    private fun decodePermissiveBase64(s: String): ByteArray {
        return runCatching { Base64.getUrlDecoder().decode(s) }
            .recoverCatching { Base64.getDecoder().decode(s) }
            .getOrThrow()
    }
}
